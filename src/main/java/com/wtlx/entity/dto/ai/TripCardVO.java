package com.wtlx.entity.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 结论正文下方挂载的"相关游记 / 攻略"卡片。
 * <p>
 * 由后端相似度检索产出（不依赖模型输出 id），未检索到相关内容时列表为空，
 * 前端据此不渲染卡片区。
 *
 * @author 金小梁
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripCardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 来源类型：BLOG=站内游记，GUIDE=官方攻略
     */
    private String sourceType;

    /**
     * 来源业务主键
     */
    private Long sourceId;

    /**
     * 标题
     */
    private String title;

    /**
     * 封面图（游记取首图，攻略取主图）
     */
    private String cover;

    /**
     * 游记作者昵称（攻略为空）
     */
    private String authorName;

    /**
     * 点赞数
     */
    private Integer liked;

    /**
     * 命中片段摘要
     */
    private String summary;

    /**
     * 相似度得分，用于排序与调试
     */
    private Double score;
}
