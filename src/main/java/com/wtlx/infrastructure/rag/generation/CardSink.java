package com.wtlx.infrastructure.rag.generation;

import com.wtlx.domain.entity.dto.ai.TripCardVO;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 请求级卡片汇聚出口。
 * <p>
 * 模型调用 {@code retrieveKnowledge} 时，工具在一次检索后把命中内容经
 * {@link RagCardAssembler} 组装为卡片写入本对象；本对象按 {@code 来源类型:来源id}
 * 去重、同一来源保留最高分，并把<b>全量</b>集合广播给前端——SSE 的 {@code cards}
 * 事件是覆盖语义，发全量即幂等更新。
 * <p>
 * 实例由 {@code TripAgentServiceImpl} 每轮对话创建一个，挂到 {@code RunnableConfig}
 * 的 metadata 上（键为 {@link #METADATA_KEY}）随请求下发给工具。之所以不用全局注册表
 * 按会话寻址：RAG 工具执行在 Reactor 线程上够不到请求级的 {@code SseEmitter}，而
 * {@code RunnableConfig} 本身就会经 {@code ToolContextHelper.getConfig(ToolContext)}
 * 流到工具里，出口随请求走即可——无需注册/注销的生命周期管理，并发会话天然隔离。
 * <p>
 * 线程安全：模型可能并行发起多个工具调用，故读写都在同一把锁内。
 * <p>
 * <b>注意</b>：实例挂在 {@code RunnableConfig} 的 metadata 上，当前使用的
 * {@code MemorySaver} 是 JVM 内存实现、不做序列化，因此安全；若将来换成持久化 Saver，
 * 需改为只传一个可序列化的标识，再由请求级注册表反查实例。
 *
 * @author 金小梁
 */
@Slf4j
public class CardSink {

    /**
     * 在 {@code RunnableConfig} metadata 中的键名
     */
    public static final String METADATA_KEY = "tripCardSink";

    /**
     * 当前累计的卡片，key 为 {@code 来源类型:来源id}；LinkedHashMap 保留加入顺序
     */
    private final Map<String, TripCardVO> cards = new LinkedHashMap<>();

    /**
     * 集合发生变化后的广播出口，收到的是应展示的全量集合
     */
    private final Consumer<List<TripCardVO>> broadcaster;

    public CardSink(Consumer<List<TripCardVO>> broadcaster) {
        this.broadcaster = broadcaster;
    }

    /**
     * 合并一批工具侧卡片并广播全量集合。
     * <p>
     * 集合无变化时不重复广播。广播失败只记日志：卡片是增强体验，
     * 绝不能因此影响模型拿到的检索结果。
     *
     * @param incoming 工具侧检索产出的卡片
     */
    public synchronized void publish(List<TripCardVO> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (TripCardVO card : incoming) {
            if (card == null || card.getSourceId() == null) {
                continue;
            }
            String key = card.getSourceType() + ":" + card.getSourceId();
            TripCardVO existed = cards.get(key);
            if (existed == null) {
                cards.put(key, card);
                changed = true;
            } else if (card.getScore() != null
                    && (existed.getScore() == null || card.getScore() > existed.getScore())) {
                existed.setScore(card.getScore());
                changed = true;
            }
        }
        if (!changed || broadcaster == null) {
            return;
        }
        try {
            broadcaster.accept(snapshot());
        } catch (Exception e) {
            log.warn("卡片广播失败，本次不推送前端", e);
        }
    }

    /**
     * 当前累计卡片快照；供 done / cancelled 事件与落库使用
     *
     * @return 卡片列表，可能为空但绝不为 null
     */
    public synchronized List<TripCardVO> snapshot() {
        return new ArrayList<>(cards.values());
    }
}
