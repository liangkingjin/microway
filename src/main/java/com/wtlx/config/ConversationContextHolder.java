package com.wtlx.config;

/**
 * 对话上下文持有者
 * 用于在 Service 层和拦截器之间传递 conversationId
 */
public class ConversationContextHolder {

    private static final ThreadLocal<String> CONVERSATION_ID = new ThreadLocal<>();

    public static void setConversationId(String conversationId) {
        CONVERSATION_ID.set(conversationId);
    }

    public static String getConversationId() {
        return CONVERSATION_ID.get();
    }

    public static void clear() {
        CONVERSATION_ID.remove();
    }
}
