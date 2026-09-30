package com.wtlx.entity.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 一条思考步骤（工具调用 / 检索 / 推理 / 图节点）。
 * <p>
 * 与"最终结论正文"分离输出，前端折叠展示，不参与正文渲染。
 *
 * @author 金小梁
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThinkingStepVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 步骤序号，从 1 开始；同一序号重复推送表示"更新该步骤"（工具参数逐步补全）
     */
    private Integer seq;

    /**
     * 步骤类型：tool=工具调用，retrieval=检索，reasoning=模型推理，node=图节点
     */
    private String type;

    /**
     * 步骤标题，如工具名、检索源名称
     */
    private String label;

    /**
     * 步骤正文，可能是工具入参、检索结果摘要或推理文本
     */
    private String text;
}
