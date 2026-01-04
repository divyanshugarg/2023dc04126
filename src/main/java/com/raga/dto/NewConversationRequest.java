package com.raga.dto;

import lombok.Data;

@Data
public class NewConversationRequest {
    private boolean deleteCurrentThread = false; // Default to false - only clear JVM memory
}

