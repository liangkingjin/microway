package com.wtlx.infrastructure.memory.retrieval;

import com.alibaba.cloud.ai.graph.agent.tools.ToolContextHelper;
import com.wtlx.infrastructure.memory.context.MemoryContextBuilder;
import com.wtlx.infrastructure.memory.support.MemoryConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 用户长期记忆召回工具（模型可主动调用）。
 * <p>
 * 与 {@code LongTermMemoryInterceptor} 的<b>主动注入</b>互补，这里是<b>被动召回</b>：
 * <ul>
 *     <li>主动注入解决"我应该知道什么"——每轮都带上高价值画像，模型无需思考；</li>
 *     <li>被动召回解决"我需要确认什么"——当用户问"你还记得我的口味吗""我之前说想去哪"
 *         这类元问题，或模型需要针对某个维度深挖时，由模型自己发起检索。</li>
 * </ul>
 * 只有被动召回会让用户感觉"系统真的记得我"，但只有主动注入才能保证模型在规划行程时
 * 真的用上了偏好。两者缺一不可。
 * <p>
 * 身份来源与注入链路一致：从 {@code RunnableConfig} 元数据取当前用户 id。
 * 工具执行在 Reactor 线程上够不到 ThreadLocal，这条通道与 RAG 卡片出口用的是同一条。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class MemoryRecallTool {

    /**
     * 工具召回的条数上限：比自动注入更少，因为调用方是模型，它会按需多次调用
     */
    private static final int RECALL_TOP_N = 6;

    @Resource
    private MemoryContextBuilder memoryContextBuilder;

    /**
     * 检索当前用户的长期记忆
     *
     * @param query       想确认的信息，例如"用户的饮食偏好""用户的出行时间"
     * @param toolContext 框架注入的工具上下文（不进入工具 schema），用于取记忆作用域
     * @return 记忆文本；无用户或无记忆时返回明确说明
     */
    @Tool(description = "检索系统已沉淀的当前用户长期记忆，包含跨会话累积的旅行偏好、忌口、预算档位、"
            + "同行情况、出行计划等。当需要确认用户的个性化偏好，或用户询问"
            + "「你还记得我的喜好吗」「我之前说过什么」时调用本工具")
    public String recallUserMemory(
            @ToolParam(description = "想确认的用户信息，例如「用户的饮食忌口」「用户偏好哪种住宿」") String query,
            ToolContext toolContext) {
        Long userId = resolveUserId(toolContext);
        if (userId == null) {
            log.debug("工具执行上下文缺少用户身份，无法召回长期记忆");
            return "当前会话未关联到用户身份，无法检索长期记忆。请直接询问用户相关偏好。";
        }
        if (!StringUtils.hasText(query)) {
            return "请给出需要确认的信息维度后再检索。";
        }

        MemoryContextBuilder.MemoryPrompt prompt = memoryContextBuilder.build(userId, query, RECALL_TOP_N);
        if (prompt.isEmpty()) {
            log.info("长期记忆召回为空, userId: {}, query: {}", userId, query);
            return "系统中暂未沉淀该用户的长期记忆（查询：" + query + "）。";
        }
        log.info("长期记忆召回成功, userId: {}, query: {}, 命中 {} 条", userId, query, prompt.hits().size());
        return prompt.text();
    }

    /**
     * 从工具上下文取当前用户 id
     * <p>
     * {@code RunnableConfig} 由框架放在 {@code ToolContext} 的 {@code _AGENT_CONFIG_} 键上，
     * 用户 id 挂在它的 metadata 里随请求下发。
     */
    private Long resolveUserId(ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        return ToolContextHelper.getConfig(toolContext)
                .flatMap(config -> config.metadata(MemoryConstants.USER_ID_METADATA))
                .filter(Long.class::isInstance)
                .map(Long.class::cast)
                .orElse(null);
    }
}
