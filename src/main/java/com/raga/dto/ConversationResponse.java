package com.raga.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {
    private String threadId;
    private String response;
    private boolean success;
    private String errorMessage;
    private int turnCount;
}

