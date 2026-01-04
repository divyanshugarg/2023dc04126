package com.raga.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.raga.dto.ConversationRequest;
import com.raga.dto.ConversationResponse;
import com.raga.dto.NewConversationRequest;
import com.raga.service.DialogueStateService;
import com.raga.service.OpenAIService;
import com.raga.service.SafetyFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/conversation")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ConversationController {

    private final OpenAIService openAIService;
    private final SafetyFilterService safetyFilterService;
    private final DialogueStateService dialogueStateService;
    private final com.raga.service.OrderService orderService = new com.raga.service.OrderService();

    @PostMapping("/chat")
    public ResponseEntity<ConversationResponse> chat(@RequestBody ConversationRequest request) {
        try {
            // Sanitize and validate input
            SafetyFilterService.SafetyResult safetyResult = safetyFilterService.sanitizeAndValidate(request.getMessage());
            
            if (!safetyResult.isAllowed()) {
                return ResponseEntity.badRequest()
                        .body(ConversationResponse.builder()
                                .success(false)
                                .errorMessage(safetyResult.getRejectionReason())
                                .build());
            }

            String sanitizedMessage = safetyResult.getSanitizedInput();
            String threadId = request.getThreadId();
            DialogueStateService.ConversationState state;
            String runId;

            // Check if this is the first message (no threadId or thread not active)
            boolean isFirstMessage = (threadId == null || threadId.isEmpty() || !dialogueStateService.isActive(threadId));

            if (isFirstMessage) {
                // First message: Create thread and run in one API call
                log.info("Creating new thread and run for first message");
                CompletableFuture<OpenAIService.ThreadRunResult> threadRunFuture = 
                    openAIService.createThreadAndRun(sanitizedMessage).toFuture();
                OpenAIService.ThreadRunResult result = threadRunFuture.join();
                threadId = result.getThreadId();
                runId = result.getRunId();
                
                // Create state for the new thread
                state = dialogueStateService.getOrCreateState(threadId);
                state.setAssistantId(openAIService.getAssistantId());
            } else {
                // Subsequent message: Add message to existing thread, then run
                log.info("Adding message to existing thread: {}", threadId);
                state = dialogueStateService.getOrCreateState(threadId);
                
                // Add message to thread
                CompletableFuture<String> messageFuture = 
                    openAIService.addMessageToThread(threadId, sanitizedMessage).toFuture();
                String messageId = messageFuture.join();
                log.debug("Added message {} to thread {}", messageId, threadId);
                
                // Run assistant
                CompletableFuture<String> runFuture = openAIService.runAssistant(threadId).toFuture();
                runId = runFuture.join();
            }

            // Poll for completion
            String response = pollForResponse(threadId, runId);

            // Update dialogue state
            dialogueStateService.updateState(threadId, sanitizedMessage, response);

            return ResponseEntity.ok(ConversationResponse.builder()
                    .threadId(threadId)
                    .response(response)
                    .success(true)
                    .turnCount(state.getTurnCount())
                    .build());

        } catch (Exception e) {
            log.error("Error processing conversation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ConversationResponse.builder()
                            .success(false)
                            .errorMessage("An error occurred while processing your request: " + e.getMessage())
                            .build());
        }
    }

    @PostMapping("/new")
    public ResponseEntity<ConversationResponse> startNewConversation(@RequestBody(required = false) NewConversationRequest request) {
        try {
            boolean deleteThread = request != null && request.isDeleteCurrentThread();
            
            // Get current thread ID if exists
            String currentThreadId = dialogueStateService.getCurrentThreadId();
            
            if (deleteThread && currentThreadId != null && !currentThreadId.isEmpty()) {
                // Delete thread from OpenAI
                log.info("Deleting thread from OpenAI: {}", currentThreadId);
                CompletableFuture<Boolean> deleteFuture = openAIService.deleteThread(currentThreadId).toFuture();
                boolean deleted = deleteFuture.join();
                if (deleted) {
                    log.info("Successfully deleted thread: {}", currentThreadId);
                } else {
                    log.warn("Failed to delete thread: {}", currentThreadId);
                }
            }
            
            // Clear JVM memory (always done)
            dialogueStateService.clearAllStates();
            log.info("Cleared all conversation states from JVM memory");
            
            return ResponseEntity.ok(ConversationResponse.builder()
                    .threadId(null) // No thread created yet - will be created on first message
                    .response("New conversation ready. Send your first message to start!")
                    .success(true)
                    .turnCount(0)
                    .build());
        } catch (Exception e) {
            log.error("Error starting new conversation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ConversationResponse.builder()
                            .success(false)
                            .errorMessage("Failed to start new conversation: " + e.getMessage())
                            .build());
        }
    }

    @GetMapping("/status/{threadId}")
    public ResponseEntity<ConversationResponse> getStatus(@PathVariable String threadId) {
        DialogueStateService.ConversationState state = dialogueStateService.getState(threadId);
        
        if (state == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(ConversationResponse.builder()
                .threadId(threadId)
                .success(true)
                .turnCount(state.getTurnCount())
                .build());
    }

    private String pollForResponse(String threadId, String runId) {
        int maxAttempts = 30;
        int attempt = 0;
        int pollIntervalMs = 1000;

        while (attempt < maxAttempts) {
            try {
                CompletableFuture<JsonNode> runDetailsFuture = openAIService.getRunDetails(threadId, runId).toFuture();
                JsonNode runDetails = runDetailsFuture.join();
                String status = runDetails.get("status").asText();

                if ("completed".equals(status)) {
                    CompletableFuture<String> responseFuture = openAIService.getLatestAssistantResponse(threadId).toFuture();
                    return responseFuture.join();
                } else if ("requires_action".equals(status)) {
                    // Handle tool calls
                    log.info("Run requires action - processing tool calls");
                    String result = handleToolCalls(threadId, runId, runDetails);
                    if (result != null) {
                        // After submitting tool outputs, continue polling
                        continue;
                    }
                } else if ("failed".equals(status) || "cancelled".equals(status) || "expired".equals(status)) {
                    return "The assistant run failed. Please try again.";
                }

                Thread.sleep(pollIntervalMs);
                attempt++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "Request was interrupted. Please try again.";
            } catch (Exception e) {
                log.error("Error polling for response", e);
                return "Error retrieving response. Please try again.";
            }
        }

        return "Request timed out. Please try again.";
    }

    private String handleToolCalls(String threadId, String runId, JsonNode runDetails) {
        try {
            JsonNode requiredAction = runDetails.get("required_action");
            if (requiredAction == null) {
                return null;
            }

            String type = requiredAction.get("type").asText();
            if (!"submit_tool_outputs".equals(type)) {
                log.warn("Unknown required_action type: {}", type);
                return null;
            }

            JsonNode submitToolOutputs = requiredAction.get("submit_tool_outputs");
            if (submitToolOutputs == null) {
                return null;
            }

            JsonNode toolCalls = submitToolOutputs.get("tool_calls");
            if (toolCalls == null || !toolCalls.isArray()) {
                return null;
            }

            java.util.List<Map<String, Object>> toolOutputs = new java.util.ArrayList<>();

            for (JsonNode toolCall : toolCalls) {
                String toolCallId = toolCall.get("id").asText();
                JsonNode function = toolCall.get("function");
                if (function == null) {
                    continue;
                }

                String functionName = function.get("name").asText();
                JsonNode functionArguments = null;
                try {
                    String argumentsStr = function.get("arguments").asText();
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    functionArguments = mapper.readTree(argumentsStr);
                } catch (Exception e) {
                    log.error("Error parsing function arguments", e);
                    continue;
                }

                log.info("Processing tool call: {} with function: {}", toolCallId, functionName);

                String output = null;
                if ("generate_test_order_only_on_request".equals(functionName)) {
                    // Extract SKU ID from arguments
                    String skuId = orderService.extractSkuId(functionArguments);
                    if (skuId != null && !skuId.isEmpty()) {
                        output = orderService.generateOrder(skuId);
                    } else {
                        output = "Error: SKU ID is required but not provided";
                    }
                } else {
                    output = "Unknown function: " + functionName;
                }

                Map<String, Object> toolOutput = new HashMap<>();
                toolOutput.put("tool_call_id", toolCallId);
                toolOutput.put("output", output);
                toolOutputs.add(toolOutput);
            }

            if (!toolOutputs.isEmpty()) {
                // Submit all tool outputs
                CompletableFuture<JsonNode> submitFuture = 
                    openAIService.submitToolOutputs(threadId, runId, toolOutputs).toFuture();
                submitFuture.join();
                log.info("Submitted {} tool outputs", toolOutputs.size());
                return "Tool outputs submitted";
            }

            return null;
        } catch (Exception e) {
            log.error("Error handling tool calls", e);
            return null;
        }
    }
}
