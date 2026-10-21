package com.wtlx.domain.entity.memory.enums;

/**
 * 长期记忆类型。
 * <p>
 * {@code stable} 标记决定该类型落在检索结果的哪一层（见 {@code MemoryContextBuilder} 的冷热分层）：
 * <ul>
 *     <li><b>稳定层</b>（{@code stable = true}）：描述"这个人长期是什么样"，无有效期，
 *         始终参与上下文注入，跨会话复用价值最高；</li>
 *     <li><b>时效层</b>（{@code stable = false}）：描述"这一次出行的具体安排"，
 *         通常带 {@code validTo}，过期即自动失效，清理策略会优先回收。</li>
 * </ul>
 *
 * @author 金小梁
 */
public enum MemoryType {

    /**
     * 偏好（喜欢什么）
     */
    PREFERENCE("PREFERENCE", "偏好", true),

    /**
     * 避讳 / 不喜欢（讨厌什么）
     */
    DISLIKE("DISLIKE", "避讳", true),

    /**
     * 硬性约束：不可协商的条件，如"带 5 岁小孩""只能周末出行"
     */
    CONSTRAINT("CONSTRAINT", "硬性约束", true),

    /**
     * 兴趣标签，如拍照、徒步、观星
     */
    INTEREST("INTEREST", "兴趣", true),

    /**
     * 旅行风格：穷游 / 深度游 / 打卡等
     */
    TRAVEL_STYLE("TRAVEL_STYLE", "旅行风格", true),

    /**
     * 饮食偏好与忌口
     */
    DIETARY("DIETARY", "饮食偏好", true),

    /**
     * 住宿偏好
     */
    ACCOMMODATION("ACCOMMODATION", "住宿偏好", true),

    /**
     * 交通偏好
     */
    TRANSPORT("TRANSPORT", "交通偏好", true),

    /**
     * 同行情况（独自 / 情侣 / 亲子 / 朋友团体）
     */
    PARTY("PARTY", "同行情况", true),

    /**
     * 预算档位（时效性弱于行程，故归入时效层，避免长期占用上下文预算）
     */
    BUDGET("BUDGET", "预算档位", false),

    /**
     * 具体行程计划，如"明年暑假去云南"
     */
    TRIP_PLAN("TRIP_PLAN", "行程计划", false),

    /**
     * 其他可用事实
     */
    FACT("FACT", "其他事实", false);

    private final String code;

    private final String label;

    /**
     * 是否属于长期稳定层（无有效期、始终注入上下文）
     */
    private final boolean stable;

    MemoryType(String code, String label, boolean stable) {
        this.code = code;
        this.label = label;
        this.stable = stable;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isStable() {
        return stable;
    }

    /**
     * 按编码解析类型；无法识别时兜底为 {@link #FACT}，避免模型偶发输出非法枚举值导致整批抽取失败。
     *
     * @param code 模型输出的类型编码
     * @return 记忆类型，永不为 null
     */
    public static MemoryType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return FACT;
        }
        String normalized = code.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        for (MemoryType type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }
        return FACT;
    }
}
