package com.raga.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class DialogueStateService {

    // Store conversation state per thread
    private final Map<String, ConversationState> conversationStates = new ConcurrentHashMap<>();

    /**
     * Initialize or get conversation state for a thread
     */
    public ConversationState getOrCreateState(String threadId) {
        return conversationStates.computeIfAbsent(threadId, k -> {
            ConversationState state = new ConversationState();
            state.setThreadId(threadId);
            state.setCreatedAt(LocalDateTime.now());
            log.info("Created new conversation state for thread: {}", threadId);
            return state;
        });
    }

    /**
     * Update conversation state
     */
    public void updateState(String threadId, String lastUserMessage, String lastAssistantResponse) {
        ConversationState state = getOrCreateState(threadId);
        state.setLastUserMessage(lastUserMessage);
        state.setLastAssistantResponse(lastAssistantResponse);
        state.setLastUpdatedAt(LocalDateTime.now());
        state.incrementTurnCount();
    }

    /**
     * Get conversation state
     */
    public ConversationState getState(String threadId) {
        return conversationStates.get(threadId);
    }

    /**
     * Clear conversation state (for starting new conversation)
     */
    public void clearState(String threadId) {
        conversationStates.remove(threadId);
        log.info("Cleared conversation state for thread: {}", threadId);
    }

    /**
     * Check if conversation is active
     */
    public boolean isActive(String threadId) {
        return conversationStates.containsKey(threadId);
    }

    /**
     * Clear all conversation states (for starting new conversation)
     */
    public void clearAllStates() {
        conversationStates.clear();
        log.info("Cleared all conversation states from memory");
    }

    /**
     * Get the current active thread ID (most recently updated)
     */
    public String getCurrentThreadId() {
        if (conversationStates.isEmpty()) {
            return null;
        }
        // Return the most recently updated thread
        return conversationStates.values().stream()
                .max((s1, s2) -> {
                    LocalDateTime t1 = s1.getLastUpdatedAt() != null ? s1.getLastUpdatedAt() : s1.getCreatedAt();
                    LocalDateTime t2 = s2.getLastUpdatedAt() != null ? s2.getLastUpdatedAt() : s2.getCreatedAt();
                    if (t1 == null && t2 == null) return 0;
                    if (t1 == null) return -1;
                    if (t2 == null) return 1;
                    return t1.compareTo(t2);
                })
                .map(ConversationState::getThreadId)
                .orElse(null);
    }

    @Data
    public static class ConversationState {
        private String threadId;
        private String assistantId;
        private String lastUserMessage;
        private String lastAssistantResponse;
        private LocalDateTime createdAt;
        private LocalDateTime lastUpdatedAt;
        private int turnCount = 0;
        private Map<String, Object> context = new ConcurrentHashMap<>();

        public void incrementTurnCount() {
            this.turnCount++;
        }

        public void setContext(String key, Object value) {
            this.context.put(key, value);
        }

        public Object getContext(String key) {
            return this.context.get(key);
        }
    }
}

