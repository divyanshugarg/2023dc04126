package com.raga.controller;

import com.raga.dto.OrderRequest;
import com.raga.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class OrderController {

    @PostMapping("/create")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest request) {
        try {
            String skuId = request.getSkuId();
            
            if (skuId == null || skuId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(OrderResponse.builder()
                                .success(false)
                                .message("SKU ID is required")
                                .build());
            }

            // Generate order number as current epoch timestamp
            long orderNumber = System.currentTimeMillis();
            
            log.info("Created order {} for SKU: {}", orderNumber, skuId);
            
            return ResponseEntity.ok(OrderResponse.builder()
                    .orderNumber(String.valueOf(orderNumber))
                    .skuId(skuId)
                    .success(true)
                    .message("Order created successfully")
                    .build());
        } catch (Exception e) {
            log.error("Error creating order", e);
            return ResponseEntity.internalServerError()
                    .body(OrderResponse.builder()
                            .success(false)
                            .message("Failed to create order: " + e.getMessage())
                            .build());
        }
    }
}

