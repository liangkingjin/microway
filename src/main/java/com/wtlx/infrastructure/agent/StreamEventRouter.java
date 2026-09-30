package com.wtlx.infrastructure.agent;

import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.wtlx.domain.entity.dto.ai.ThinkingStepVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 流式事件分流器：把 ReactAgent 流出的 {@link StreamingOutput} 拆成两类产物
 * <ul>
 *     <li><b>思考步骤</b>——工具调用、检索、推理过程，经 {@link StreamEventRouter.StreamSink#onThinking} 输出；</li>
 *     <li><b>结论正文</b>——模型输出中位于 {@code <thinking>} 块之外的文本，经
 *         {@link StreamEventRouter.StreamSink#onContent} 输出。</li>
 * </ul>
 * 归类规则：
 * <ol>
 *     <li>{@code AGENT_MODEL_STREAMING}：交给 {@link ThinkingTagParser} 按标记分流；</li>
 *     <li>{@code AGENT_TOOL_STREAMING/FINISHED}：整段归为一步工具调用，
 *         流式期间只推送一次步骤头，结束（或被打断）时推送完整内容，避免参数逐字刷屏；</li>
 *     <li>{@code AGENT_MODEL_FINISHED}：冲刷标记解析缓冲区；</li>
 *     <li>其余（hook、节点）事件不产生用户可见内容。</li>
 * </ol>
 *
 * @author 金小梁
 */
@Slf4j
public class StreamEventRouter {

    /**
     * 单条思考步骤正文的最大长度，超出截断，避免巨型工具返回撑爆前端
     */
    private static final int STEP_TEXT_LIMIT = 300;

    /**
     * 事件出口
     */
    public interface StreamSink {
        /**
         * 新增或更新一条思考步骤（按 seq 幂等覆盖）
         *
         * @param step 步骤
         */
        void onThinking(ThinkingStepVO step);

        /**
         * 追加一段结论正文
         *
         * @param chunk 正文增量
         */
        void onContent(String chunk);
    }

    private final StreamSink sink;

    private final ThinkingTagParser parser = new ThinkingTagParser();

    private int seq = 0;

    /**
     * 当前未闭合的工具步骤
     */
    private ThinkingStepVO openToolStep;

    private final StringBuilder openToolText = new StringBuilder();

    /**
     * 当前未闭合的推理步骤（连续的 {@code <thinking>} 片段合并为一步）
     */
    private ThinkingStepVO openReasoningStep;

    public StreamEventRouter(StreamSink sink) {
        this.sink = sink;
    }

    /**
     * 处理一个流事件
     *
     * @param nodeOutput ReactAgent 流出的对象
     */
    public void accept(Object nodeOutput) {
        if (!(nodeOutput instanceof StreamingOutput<?> output)) {
            return;
        }
        OutputType type = output.getOutputType();
        if (type == null) {
            return;
        }
        switch (type) {
            case AGENT_MODEL_STREAMING -> handleModelStreaming(output);
            case AGENT_MODEL_FINISHED -> finish();
            case AGENT_TOOL_STREAMING -> handleToolStreaming(output);
            case AGENT_TOOL_FINISHED -> handleToolFinished(output);
            default -> {
            }
        }
    }

    /**
     * 流结束：冲刷缓冲，闭合未完成的步骤
     */
    public void finish() {
        for (ThinkingTagParser.Segment segment : parser.flush()) {
            dispatch(segment);
        }
        closeReasoning();
        closeTool();
    }

    private void handleModelStreaming(StreamingOutput<?> output) {
        // 模型开始输出说明上一轮工具调用已结束
        closeTool();
        Message message = output.message();
        if (message == null) {
            return;
        }
        String text = message.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        for (ThinkingTagParser.Segment segment : parser.feed(text)) {
            dispatch(segment);
        }
    }

    private void dispatch(ThinkingTagParser.Segment segment) {
        if (segment.getText() == null || segment.getText().isEmpty()) {
            return;
        }
        if (segment.getKind() == ThinkingTagParser.Kind.CONTENT) {
            closeReasoning();
            sink.onContent(segment.getText());
        } else {
            appendReasoning(segment.getText());
        }
    }

    private void handleToolStreaming(StreamingOutput<?> output) {
        if (openToolStep == null) {
            closeReasoning();
            seq++;
            openToolStep = ThinkingStepVO.builder()
                    .seq(seq)
                    .type("tool")
                    .label(extractToolLabel(output))
                    .text("")
                    .build();
            openToolText.setLength(0);
            sink.onThinking(openToolStep);
        }
        String chunk = output.chunk();
        if (chunk != null && !chunk.isEmpty()) {
            openToolText.append(chunk);
        }
    }

    private void handleToolFinished(StreamingOutput<?> output) {
        if (openToolStep == null) {
            // 只有 FINISHED 没有 STREAMING：补一步，保证工具可见
            seq++;
            openToolStep = ThinkingStepVO.builder()
                    .seq(seq)
                    .type("tool")
                    .label(extractToolLabel(output))
                    .text("")
                    .build();
            openToolText.setLength(0);
        }
        String result = output.message() == null ? null : output.message().getText();
        String text = (result != null && !result.isBlank()) ? result : openToolText.toString();
        openToolStep.setText(abbreviate(text));
        sink.onThinking(openToolStep);
        openToolStep = null;
        openToolText.setLength(0);
    }

    private void closeTool() {
        if (openToolStep != null) {
            openToolStep.setText(abbreviate(openToolText.toString()));
            sink.onThinking(openToolStep);
            openToolStep = null;
            openToolText.setLength(0);
        }
    }

    /**
     * 新建推理步骤时下发"建立"事件，之后每次只下发新增文本（append=true）。
     * 推理内容往往上千字，若每帧都发全量，传输量会随帧数平方增长。
     */
    private void appendReasoning(String text) {
        if (openReasoningStep == null) {
            seq++;
            openReasoningStep = ThinkingStepVO.builder()
                    .seq(seq)
                    .type("reasoning")
                    .label("推理")
                    .text("")
                    .append(false)
                    .build();
            sink.onThinking(openReasoningStep);
        }
        sink.onThinking(ThinkingStepVO.builder()
                .seq(seq)
                .type("reasoning")
                .label("推理")
                .text(text)
                .append(true)
                .build());
    }


    private void closeReasoning() {
        openReasoningStep = null;
    }

    /**
     * 取工具名：优先工具调用名，其次消息文本
     */
    private String extractToolLabel(StreamingOutput<?> output) {
        Message message = output.message();
        if (message instanceof AssistantMessage assistantMessage) {
            List<AssistantMessage.ToolCall> calls = assistantMessage.getToolCalls();
            if (calls != null && !calls.isEmpty()) {
                return calls.stream()
                        .map(AssistantMessage.ToolCall::name)
                        .filter(name -> name != null && !name.isBlank())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("工具调用");
            }
        }
        if (message != null && message.getText() != null && !message.getText().isBlank()) {
            return abbreviate(message.getText());
        }
        return "工具调用";
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= STEP_TEXT_LIMIT) {
            return trimmed;
        }
        return trimmed.substring(0, STEP_TEXT_LIMIT) + "…";
    }
}
