package com.gamesense.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

// ========== Dead Letter Queue Service ==========
@Service
@RequiredArgsConstructor
@Slf4j
public class DeadLetterQueueService {

    private final MongoTemplate mongoTemplate;

    public void logFailedOperation(String operation, String userId, String resourceId, String status, String error) {
        log.error("Failed operation added to DLQ: operation={}, userId={}, resourceId={}, status={}, error={}",
            operation, userId, resourceId, status, error);
        
        // Persist to DB for manual reconciliation
        try {
            mongoTemplate.save(Map.of(
                "operation", operation,
                "userId", userId,
                "resourceId", resourceId,
                "payload", status, 
                "error", error,
                "failedAt", LocalDateTime.now(),
                "retryCount", 0,
                "resolved", false
            ), "dead_letter_queue");            
        } catch (Exception e) {
            // If even the DLQ fails, we must fall back to the logs (last resort)
            log.error("CRITICAL: Failed to write to DLQ. Data consistency risk.", e);
        }
    }
}