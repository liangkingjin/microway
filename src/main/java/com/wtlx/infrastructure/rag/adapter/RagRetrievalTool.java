package com.wtlx.infrastructure.rag.adapter;

import com.alibaba.cloud.ai.graph.agent.tools.ToolContextHelper;
import com.wtlx.domain.entity.dto.ai.TripCardVO;
import com.wtlx.infrastructure.rag.RagDocType;
import com.wtlx.infrastructure.rag.config.RagProperties;
import com.wtlx.infrastructure.rag.generation.CardSink;
import com.wtlx.infrastructure.rag.generation.RagCardAssembler;
import com.wtlx.infrastructure.rag.retriever.RagHit;
import com.wtlx.infrastructure.rag.retriever.RagSearchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 知识库检索工具
 * <p>
 * 将 RAG（Retrieval Augmented Generation，检索增强生成）的检索阶段封装为 Agent 可调用的工具：
 * Agent 需要旅游领域参考时主动调用，从<b>站内已发布的旅游游记与官方攻略</b>中做相似度检索，
 * 拿回带来源标识的片段，再基于检索结果作答。
 * <p>
 * <b>本工具是前端卡片的唯一生产者</b>：同一次检索同时服务两件事——头部若干条留给模型作上下文，
 * 其中得分过卡片阈值的部分则组装成卡片，经 {@link CardSink} 推给前端。之所以由模型改写后的
 * 查询词驱动卡片：多轮追问时用户原话（如"那住宿呢"）缺主题词，向量检索会命中大量仅共享泛词的
 * 无关来源，模型改写的查询词自带对话上下文，召回质量更高。
 * <p>
 * 卡片数据一律取自向量库 metadata 并由 {@link RagCardAssembler} 组装，<b>不解析、不采信模型的
 * 任何输出</b>——模型只贡献查询词与调用时机，从机制上排除卡片 id 幻觉。
 * <p>
 * 检索参数取自 {@code trip-agent.rag.*}：宽召回用 {@code candidate-top-k} + {@code search-threshold}
 * （同时覆盖给模型的条数与卡片候选），给模型的上下文取前 {@code search-top-k} 条，
 * 卡片取其中得分 {@code >= card-threshold} 的前 {@code card-top-k} 条。
 */
@Slf4j
@Component
public class RagRetrievalTool {

    @Resource
    private RagSearchService ragSearchService;

    @Resource
    private RagProperties ragProperties;

    @Resource
    private RagCardAssembler ragCardAssembler;

    /**
     * 从站内旅游游记与官方攻略中检索与问题相关的内容
     *
     * @param query       用于检索的查询内容，通常是用户的旅游相关问题
     * @param toolContext 框架注入的工具上下文（不进入工具 schema），用于取本轮的卡片出口
     * @return 检索到的内容片段（带来源标题与类型）；未检索到时返回明确提示
     */
    @Tool(description = "从站内已发布的旅游游记与官方攻略中检索与用户问题相关的内容，覆盖目的地玩法、行程安排、食宿交通等真实经验；需要旅游参考知识时调用本工具")
    public String retrieveKnowledge(
            @ToolParam(description = "用于检索的查询内容，通常是用户的旅游相关问题") String query,
            ToolContext toolContext) {
        log.info("RAG 站内检索，query: {}", query);

        List<RagHit> hits = ragSearchService.search(query, resolveCandidateTopK(),
                ragProperties.getSearchThreshold(), List.of(RagDocType.BLOG, RagDocType.GUIDE));

        if (hits.isEmpty()) {
            log.info("RAG 未检索到相关内容，query: {}", query);
            return "站内未检索到与该问题相关的游记或攻略: " + query;
        }

        // 卡片与模型上下文同源：一次检索两处产出，不重复花 embedding 与向量检索的钱
        publishCards(toolContext, hits);

        String context = hits.stream()
                .limit(ragProperties.getSearchTopK())
                .map(this::format)
                .collect(Collectors.joining("\n\n---\n\n"));

        log.info("RAG 检索到 {} 条相关内容", hits.size());
        return context;
    }

    /**
     * 把本次命中的高分段组装成卡片并推给前端。
     * <p>
     * 卡片是增强体验：取不到出口或组装失败都只记日志，绝不影响模型拿到的检索结果。
     *
     * @param toolContext 工具上下文，承载本轮的卡片出口
     * @param hits        本次检索命中，已按相似度降序
     */
    private void publishCards(ToolContext toolContext, List<RagHit> hits) {
        try {
            CardSink sink = resolveSink(toolContext);
            if (sink == null) {
                log.debug("工具执行上下文缺少卡片出口，本轮不产出卡片");
                return;
            }
            List<RagHit> candidates = hits.stream()
                    .filter(hit -> hit.getScore() >= ragProperties.getCardThreshold())
                    .limit(ragProperties.getCardTopK())
                    .toList();
            if (candidates.isEmpty()) {
                log.info("RAG 工具侧无卡片候选（阈值 {}），命中 {} 条均低于阈值",
                        ragProperties.getCardThreshold(), hits.size());
                return;
            }
            List<TripCardVO> cards = ragCardAssembler.assemble(candidates);
            sink.publish(cards);
            log.info("RAG 工具侧卡片产出, query 命中 {} 条, 卡片 {} 张", hits.size(), cards.size());
        } catch (Exception e) {
            log.warn("RAG 工具侧卡片产出失败，本轮降级为无卡片", e);
        }
    }

    /**
     * 从工具上下文取本轮卡片出口。
     * <p>
     * {@code RunnableConfig} 由框架放在 {@code ToolContext} 的 {@code _AGENT_CONFIG_} 键上，
     * 出口则挂在它的 metadata 里随请求下发。
     */
    private CardSink resolveSink(ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        return ToolContextHelper.getConfig(toolContext)
                .flatMap(config -> config.metadata(CardSink.METADATA_KEY))
                .filter(CardSink.class::isInstance)
                .map(CardSink.class::cast)
                .orElse(null);
    }

    /**
     * 宽召回条数：必须不小于给模型的条数与卡片取用条数，
     * 否则卡片候选会被"给模型的条数"截断，出现本来过阈值却取不到的漏召回
     */
    private int resolveCandidateTopK() {
        return Math.max(ragProperties.getCandidateTopK(),
                Math.max(ragProperties.getSearchTopK(), ragProperties.getCardTopK()));
    }

    /**
     * 格式化单条命中：带上来源标题与类型，便于模型引用与区分
     */
    private String format(RagHit hit) {
        Object title = hit.getMetadata() == null ? null : hit.getMetadata().get("title");
        Object docType = hit.getMetadata() == null ? null : hit.getMetadata().get("docType");
        String source = RagDocType.GUIDE.equals(String.valueOf(docType)) ? "官方攻略" : "站内游记";
        return "【" + source + "】" + (title == null ? "" : title) + "\n" + hit.getText();
    }
}
