package com.gamesense.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import com.gamesense.model.neo4j.GameStatus;

import java.time.LocalDateTime;
import java.util.Map;

// ========== Activity Log Service ==========
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogService {

    private final MongoTemplate mongoTemplate;

    public void logGameAddition(String userId, String gameId, GameStatus status) {
        log.info("Logging game addition: User={}, Game={}, Status={}", userId, gameId, status);
        // In a microservices architecture, this might emit an event to Kafka/RabbitMQ.        
        try {
            mongoTemplate.save(Map.of(
                "userId", userId,
                "gameId", gameId,
                "action", "ADD_GAME",
                "status", status,
                "timestamp", LocalDateTime.now(),
                "metadata", Map.of("source", "web-client")
            ), "activity_logs");
            
        } catch (Exception e) {
            log.error("Failed to persist activity log", e);
            // We do NOT re-throw here because this is a non-critical side effect.
        }
    }
}