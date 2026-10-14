package com.wtlx.domain.entity.memory;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 长期记忆变更事件（审计流），对应 PostgreSQL 表 {@code user_memory_event}。
 * <p>
 * 记忆合并与冲突校验的每一次决策都落一条事件，回答"这条记忆为什么被覆盖/失效"。
 * 冲突校验一旦出错（模型误判导致偏好被覆盖），这张表是唯一的回溯与还原依据；
 * 同时它也用于统计抽取质量（NOOP 占比过高说明抽取过于啰嗦，SUPERSEDE 占比过高说明用户偏好不稳定）。
 *
 * @author 金小梁
 */
@Data
public class UserMemoryEvent {

    private Long id;

    /**
     * 被操作记忆的对外标识；ADD 时是新增记忆的 id
     */
    private String memoryId;

    private String subjectKey;

    /**
     * 决策编码，见 {@code MemoryDecision}
     */
    private String decision;

    private String memoryType;

    private String memoryKey;

    /**
     * 变更前内容（被覆盖 / 失效的旧值）
     */
    private String beforeContent;

    /**
     * 变更后内容（新增 / 更新后的值）
     */
    private String afterContent;

    /**
     * 决策理由：规则预筛命中说明，或冲突判定模型给出的理由
     */
    private String reason;

    /**
     * 来源会话
     */
    private String sourceRef;

    private LocalDateTime createdAt;
}
