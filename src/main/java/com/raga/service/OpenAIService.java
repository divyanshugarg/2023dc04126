package com.raga.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.raga.config.OpenAIConfig;
import com.raga.config.OpenAIKeyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class OpenAIService {

    private final WebClient webClient;
    private final String apiKey;
    private final String assistantId;

    public OpenAIService(OpenAIConfig openAIConfig, OpenAIKeyConfig keyConfig) {
        String key = keyConfig.openAIApiKey();
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException("OpenAI API key is not configured. Please set OPENAI_API_KEY environment variable, configure openai.api.key in application.properties, or create open_ai_key file.");
        }
        this.apiKey = key;
        
        String baseUrl = openAIConfig.getApi().getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalStateException("OpenAI API base URL is not configured.");
        }
        
        String assistantId = openAIConfig.getAssistant().getId();
        if (assistantId == null || assistantId.isEmpty()) {
            throw new IllegalStateException("OpenAI Assistant ID is not configured. Please set openai.assistant.id in application.properties.");
        }
        this.assistantId = assistantId;
        
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + this.apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("OpenAI-Beta", "assistants=v2")
                .build();
    }

    /**
     * Get the assistant ID
     */
    public String getAssistantId() {
        return assistantId;
    }

    /**
     * Delete a thread from OpenAI
     */
    public Mono<Boolean> deleteThread(String threadId) {
        return webClient.delete()
                .uri("/threads/{threadId}", threadId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    log.info("Deleted thread with ID: {}", threadId);
                    return true;
                })
                .onErrorResume(error -> {
                    log.error("Error deleting thread: {}", threadId, error);
                    return Mono.just(false);
                });
    }

    /**
     * Create a thread and run with first message (for first message in conversation)
     */
    public Mono<ThreadRunResult> createThreadAndRun(String message) {
        Map<String, Object> request = new HashMap<>();
        request.put("assistant_id", assistantId);
        request.put("stream", false);
        
        Map<String, Object> thread = new HashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object>[] messages = new Map[1];
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", message);
        messages[0] = userMessage;
        thread.put("messages", messages);
        request.put("thread", thread);

        return webClient.post()
                .uri("/threads/runs")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    String threadId = response.get("thread_id").asText();
                    String runId = response.get("id").asText();
                    log.info("Created thread {} and run {} with first message", threadId, runId);
                    return new ThreadRunResult(threadId, runId);
                });
    }

    /**
     * Add message to existing thread and return message ID
     */
    public Mono<String> addMessageToThread(String threadId, String message) {
        Map<String, Object> messageRequest = new HashMap<>();
        messageRequest.put("role", "user");
        messageRequest.put("content", message);

        return webClient.post()
                .uri("/threads/{threadId}/messages", threadId)
                .bodyValue(messageRequest)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    String messageId = response.get("id").asText();
                    log.info("Added message {} to thread {}", messageId, threadId);
                    return messageId;
                });
    }

    /**
     * Run assistant on existing thread
     */
    public Mono<String> runAssistant(String threadId) {
        Map<String, Object> runRequest = new HashMap<>();
        runRequest.put("assistant_id", assistantId);
        runRequest.put("stream", false);

        return webClient.post()
                .uri("/threads/{threadId}/runs", threadId)
                .bodyValue(runRequest)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    String runId = response.get("id").asText();
                    log.info("Created run {} for thread {}", runId, threadId);
                    return runId;
                });
    }

    /**
     * Check run status
     */
    public Mono<String> checkRunStatus(String threadId, String runId) {
        return webClient.get()
                .uri("/threads/{threadId}/runs/{runId}", threadId, runId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> response.get("status").asText());
    }

    /**
     * Get run details (to check for requires_action)
     */
    public Mono<JsonNode> getRunDetails(String threadId, String runId) {
        return webClient.get()
                .uri("/threads/{threadId}/runs/{runId}", threadId, runId)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    /**
     * Submit tool outputs for requires_action status
     */
    public Mono<JsonNode> submitToolOutputs(String threadId, String runId, java.util.List<Map<String, Object>> toolOutputs) {
        Map<String, Object> request = new HashMap<>();
        request.put("tool_outputs", toolOutputs);

        return webClient.post()
                .uri("/threads/{threadId}/runs/{runId}/submit_tool_outputs", threadId, runId)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    /**
     * Get messages from thread
     */
    public Mono<JsonNode> getThreadMessages(String threadId) {
        return webClient.get()
                .uri("/threads/{threadId}/messages", threadId)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    /**
     * Get the latest assistant response from thread
     */
    public Mono<String> getLatestAssistantResponse(String threadId) {
        return getThreadMessages(threadId)
                .map(messages -> {
                    JsonNode data = messages.get("data");
                    if (data != null && data.isArray() && data.size() > 0) {
                        // Find the first assistant message (messages are in reverse chronological order)
                        for (JsonNode message : data) {
                            String role = message.get("role").asText();
                            if ("assistant".equals(role)) {
                                JsonNode content = message.get("content");
                                if (content != null && content.isArray() && content.size() > 0) {
                                    JsonNode textContent = content.get(0).get("text");
                                    if (textContent != null) {
                                        return textContent.get("value").asText();
                                    }
                                }
                            }
                        }
                    }
                    return "No response available";
                });
    }

    /**
     * Result class for thread and run creation
     */
    public static class ThreadRunResult {
        private final String threadId;
        private final String runId;

        public ThreadRunResult(String threadId, String runId) {
            this.threadId = threadId;
            this.runId = runId;
        }

        public String getThreadId() {
            return threadId;
        }

        public String getRunId() {
            return runId;
        }
    }
}
