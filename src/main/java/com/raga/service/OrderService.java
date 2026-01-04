package com.raga.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class OrderService {

    private final WebClient webClient;

    public OrderService() {
        this.webClient = WebClient.builder()
                .baseUrl("http://localhost:8080")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Generate order by calling the order creation API
     */
    public String generateOrder(String skuId) {
        try {
            Map<String, String> request = new HashMap<>();
            request.put("skuId", skuId);

            CompletableFuture<JsonNode> future = webClient.post()
                    .uri("/api/orders/create")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .toFuture();

            JsonNode response = future.join();
            
            Boolean success = response.get("success").asBoolean();
            if (success) {
                String orderNumber = response.get("orderNumber").asText();
                log.info("Generated order {} for SKU: {}", orderNumber, skuId);
                return "Order created successfully. Order Number: " + orderNumber + ", SKU: " + skuId;
            } else {
                String message = response.has("message") ? response.get("message").asText() : "Unknown error";
                log.error("Failed to create order: {}", message);
                return "Failed to create order: " + message;
            }
        } catch (Exception e) {
            log.error("Error calling order creation API", e);
            return "Failed to create order: " + e.getMessage();
        }
    }

    /**
     * Extract SKU ID from function arguments
     */
    public String extractSkuId(JsonNode functionArguments) {
        if (functionArguments != null && functionArguments.has("sku_id")) {
            return functionArguments.get("sku_id").asText();
        }
        return null;
    }
}

