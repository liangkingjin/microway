package com.wtlx.domain.entity.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 一轮对话的结构化结果：结论正文 + 关联卡片 + 思考步骤。
 * <p>
 * 作为 SSE {@code done} / {@code cancelled} 事件的 data（JSON）返回前端。
 *
 * @author 金小梁
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripAnswerVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 最终结论正文（markdown）
     */
    private String content;

    /**
     * 关联卡片；无命中时为空集合（不为 null）
     */
    @Builder.Default
    private List<TripCardVO> cards = new ArrayList<>();

    /**
     * 思考步骤
     */
    @Builder.Default
    private List<ThinkingStepVO> thinking = new ArrayList<>();
}
