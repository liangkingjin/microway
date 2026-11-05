package com.wtlx.domain.entity.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 景点 AI 详情：大模型生成的介绍 / 游览建议 / 文生图图片。
 * <p>
 * status 三态：{@code PENDING}（后台生成中，前端轮询）、{@code DONE}（含缓存命中）、
 * {@code FAILED}（连续生成失败，进入冷却）。
 *
 * @author 金小梁
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenicInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String status;

    private String name;

    private String intro;

    private List<String> tips;

    private List<String> images;
}
