package com.wtlx.service;

import com.wtlx.domain.entity.memory.MemoryOverviewVO;
import com.wtlx.domain.entity.memory.UserMemory;

import java.util.List;
import java.util.Map;

/**
 * 用户长期记忆管理服务。
 * <p>
 * 承担原 {@code MemoryController} 中的业务逻辑：当前用户解析、长期记忆的检索 / 统计 / 删除、
 * 以及"短期记忆 + 长期记忆"全景视图的组装。Controller 只做参数校验与结果包装。
 *
 * @author 金小梁
 */
public interface UserMemoryService {

    /**
     * 记忆全景：短期记忆（按会话）与长期记忆（按用户）对照
     *
     * @param conversationId 会话 id，可为空
     * @param query          检索词，可为空
     * @return 全景视图，作用域恒为当前登录用户
     */
    MemoryOverviewVO overview(String conversationId, String query);

    /**
     * 列举当前用户的长期记忆
     *
     * @param type   类型过滤，可为空
     * @param status 状态过滤，可为空表示不限
     * @param limit  返回条数上限
     */
    List<UserMemory> list(String type, String status, int limit);

    /**
     * 向量检索调试（含命中相似度）
     *
     * @param query 检索词
     * @param topN  返回条数上限
     * @return {@code userId} / {@code query} / {@code hitCount} / {@code hits}
     */
    Map<String, Object> search(String query, int topN);

    /**
     * 删除单条记忆（软删）
     *
     * @return 是否命中并删除
     */
    boolean deleteOne(String memoryId);

    /**
     * 物理清空当前用户的全部长期记忆（含历史行）
     *
     * @return 删除条数
     */
    int deleteAll();

    /**
     * 补建记忆相关表结构（PG 恢复后重试）
     *
     * @return DDL 失败条数，0 表示全部成功
     */
    int ensureSchema();

    /**
     * 全局记忆类型分布（运维观测）
     */
    Map<String, Integer> stats();
}
