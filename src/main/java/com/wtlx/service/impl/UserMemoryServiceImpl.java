package com.wtlx.service.impl;

import com.wtlx.domain.entity.conversation.ConversationMessage;
import com.wtlx.domain.entity.conversation.ConversationSummary;
import com.wtlx.domain.entity.memory.MemoryHit;
import com.wtlx.domain.entity.memory.MemoryOverviewVO;
import com.wtlx.domain.entity.memory.UserMemory;
import com.wtlx.infrastructure.memory.retrieval.MemoryRetrievalService;
import com.wtlx.infrastructure.memory.schema.MemorySchemaInitializer;
import com.wtlx.repository.mapper.ConversationMessageMapper;
import com.wtlx.repository.mapper.ConversationSummaryMapper;
import com.wtlx.repository.mapper.UserMemoryMapper;
import com.wtlx.service.ConversationMemoryService;
import com.wtlx.service.UserMemoryService;
import com.wtlx.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户长期记忆管理服务实现。
 * <p>
 * 记忆作用域统一取当前登录用户；{@code /ai/**} 已由 {@code LoginInterceptor} 保证登录态，
 * 因此这里直接使用 {@link UserHolder}，不做身份猜测或降级。
 *
 * @author 金小梁
 */
@Slf4j
@Service
public class UserMemoryServiceImpl implements UserMemoryService {

    /**
     * 概览里回溯的最近消息条数
     */
    private static final int OVERVIEW_RECENT_MESSAGES = 6;

    @Resource
    private MemoryRetrievalService memoryRetrievalService;

    @Resource
    private MemorySchemaInitializer memorySchemaInitializer;

    @Resource
    private UserMemoryMapper userMemoryMapper;

    @Resource
    private ConversationSummaryMapper conversationSummaryMapper;

    @Resource
    private ConversationMessageMapper conversationMessageMapper;

    @Resource
    private ConversationMemoryService conversationMemoryService;

    @Override
    public MemoryOverviewVO overview(String conversationId, String query) {
        Long userId = currentUserId();

        MemoryOverviewVO overview = new MemoryOverviewVO();
        overview.setUserId(userId);

        // 短期记忆：作用域是会话，换 threadId 就变
        if (StringUtils.hasText(conversationId)) {
            overview.setSession(buildSessionScope(conversationId));
        }

        // 长期记忆：作用域是用户，换 threadId 不变
        MemoryOverviewVO.LongTermScope longTerm = new MemoryOverviewVO.LongTermScope();
        longTerm.setActiveCount(userMemoryMapper.countActive(userId));
        longTerm.setCountByType(userMemoryMapper.countActiveByType(userId));
        if (StringUtils.hasText(query)) {
            List<MemoryHit> hits = memoryRetrievalService.retrieve(userId, query);
            overview.setHits(hits);
            longTerm.setHitCount(hits.size());
        }
        overview.setLongTerm(longTerm);
        return overview;
    }

    @Override
    public List<UserMemory> list(String type, String status, int limit) {
        return userMemoryMapper.listByUser(currentUserId(), type, status, limit);
    }

    @Override
    public Map<String, Object> search(String query, int topN) {
        Long userId = currentUserId();
        List<MemoryHit> hits = memoryRetrievalService.retrieve(userId, query, topN);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("query", query);
        payload.put("hitCount", hits.size());
        payload.put("hits", hits);
        return payload;
    }

    @Override
    public boolean deleteOne(String memoryId) {
        return userMemoryMapper.markDeleted(currentUserId(), memoryId) > 0;
    }

    @Override
    public int deleteAll() {
        return userMemoryMapper.deleteByUser(currentUserId());
    }

    @Override
    public int ensureSchema() {
        return memorySchemaInitializer.ensureSchema();
    }

    @Override
    public Map<String, Integer> stats() {
        return userMemoryMapper.countAllActiveByType();
    }

    /**
     * 记忆作用域：{@code /ai/**} 已要求登录，直接取当前用户 id
     */
    private Long currentUserId() {
        return UserHolder.getUser().getId();
    }

    /**
     * 组装短期记忆视图
     */
    private MemoryOverviewVO.SessionScope buildSessionScope(String conversationId) {
        MemoryOverviewVO.SessionScope scope = new MemoryOverviewVO.SessionScope();
        scope.setConversationId(conversationId);
        scope.setMessageCount(conversationMessageMapper.countByConversationId(conversationId));
        scope.setUserTurns(conversationMessageMapper.countUserMessages(conversationId));
        ConversationSummary summary = conversationSummaryMapper.findByConversationId(conversationId);
        if (summary != null) {
            scope.setSummary(summary.getSummary());
            scope.setSummaryMessageCount(summary.getMessageCount());
        }
        List<ConversationMessage> recent =
                conversationMemoryService.loadRecentMessages(conversationId, OVERVIEW_RECENT_MESSAGES);
        List<MemoryOverviewVO.MessagePreview> previews = new ArrayList<>(recent.size());
        for (ConversationMessage message : recent) {
            MemoryOverviewVO.MessagePreview preview = new MemoryOverviewVO.MessagePreview();
            preview.setRole(message.getRole() == null ? null : message.getRole().getCode());
            preview.setContent(abbreviate(message.getContent()));
            preview.setTime(message.getCreatedAt() == null ? null : message.getCreatedAt().toString());
            previews.add(preview);
        }
        scope.setRecentMessages(previews);
        return scope;
    }

    private String abbreviate(String content) {
        if (content == null) {
            return null;
        }
        return content.length() > 120 ? content.substring(0, 120) + "..." : content;
    }
}
