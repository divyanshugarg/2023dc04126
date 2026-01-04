package com.raga.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConversationRequest {
    @NotBlank(message = "Message cannot be empty")
    private String message;
    
    private String threadId; // Optional: if not provided, new conversation starts
}

