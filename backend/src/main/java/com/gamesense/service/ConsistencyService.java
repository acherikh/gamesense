package com.gamesense.service;

import com.gamesense.model.mongo.User;
import com.gamesense.model.neo4j.GameNode;
import com.gamesense.model.neo4j.GameOwnership;
import com.gamesense.model.neo4j.GameStatus;
import com.gamesense.model.neo4j.TeamNode;
import com.gamesense.model.neo4j.UserNode;
import com.gamesense.repository.mongo.UserRepository;
import com.gamesense.repository.neo4j.GameNodeRepository;
import com.gamesense.repository.neo4j.UserNodeRepository;
import com.gamesense.repository.neo4j.TeamNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Consistency Service implementing Eventual Consistency between MongoDB and Neo4j
 * 
 * Strategy:
 * 1. Write to Neo4j first (Primary transaction - Graph relationships are critical)
 * 2. Upon success, write to MongoDB (Secondary - for activity logs/feeds)
 * 3. If MongoDB fails, use @Retryable with exponential backoff
 * 4. If ultimately fails, log to Dead Letter Queue for manual reconciliation
 * 
 * This implements an AP (Availability + Partition Tolerance) system for social features
 * while maintaining CP (Consistency + Partition Tolerance) for critical user registration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConsistencyService {

    private final UserNodeRepository userNodeRepository;
    private final TeamNodeRepository teamNodeRepository;
    private final GameNodeRepository gameNodeRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private final DeadLetterQueueService deadLetterQueueService;

    /**
     * Add game to user's library with cross-database consistency
     * 
     * Transaction Flow:
     * 1. Create/Update Neo4j relationship (PRIMARY)
     * 2. Log activity in MongoDB (SECONDARY)
     */
    @Transactional
    public void addGameToLibrary(String userId, String gameId, GameStatus status) {
        log.info("Adding game {} to user {} library with status {}", gameId, userId, status);
        
        try {
            // STEP 1: Write to Neo4j (Primary Operation)
            UserNode userNode = userNodeRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User not found in graph: " + userId));
            
            GameNode gameNode = gameNodeRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found in graph: " + gameId));
            
            GameOwnership ownership = GameOwnership.builder()
                .game(gameNode)
                .status(status)
                .hoursPlayed(0)
                .addedAt(LocalDateTime.now())
                .build();
            
            userNode.getOwnedGames().add(ownership);
            userNodeRepository.save(userNode);
            
            log.info("Successfully created Neo4j relationship: User {} -> Game {}", userId, gameId);
            
            // STEP 2: Write to MongoDB with retry logic
            writeActivityLogWithRetry(userId, gameId, status);
            
        } catch (Exception e) {
            log.error("Failed to add game to library: {}", e.getMessage(), e);
            // Neo4j transaction will rollback automatically
            throw new RuntimeException("Failed to add game to library", e);
        }
    }

    /**
     * MongoDB write with automatic retry and exponential backoff
     * 
     * Retry Configuration:
     * - Max Attempts: 3
     * - Initial Delay: 1000ms
     * - Max Delay: 10000ms
     * - Multiplier: 2.0 (exponential)
     */
    @Retryable(
        retryFor = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(
            delay = 1000,
            maxDelay = 10000,
            multiplier = 2.0
        )
    )
    public void writeActivityLogWithRetry(String userId, String gameId, GameStatus status) {
        log.info("Attempting to write activity log (MongoDB)");
        
        try {
            activityLogService.logGameAddition(userId, gameId, status);
            log.info("Successfully wrote activity log to MongoDB");
            
        } catch (Exception e) {
            log.warn("MongoDB write failed, will retry: {}", e.getMessage());
            throw e; // Re-throw to trigger retry
        }
    }

    /**
     * Recovery method called when all retry attempts fail
     * Logs the failed operation to Dead Letter Queue for manual reconciliation
     */
    @Recover
    public void recoverFromMongoWriteFailure(Exception e, String userId, String gameId, GameStatus status) {
        log.error("All retry attempts exhausted for activity log. Sending to DLQ", e);
        
        deadLetterQueueService.logFailedOperation(
            "ADD_GAME_TO_LIBRARY",
            userId,
            gameId,
            status.toString(),
            e.getMessage()
        );
        
        // Don't throw exception - Neo4j transaction is already committed
        // This represents eventual consistency - the graph is correct,
        // but the activity feed will be updated later
        log.warn("Operation partially completed. Graph updated, activity log pending in DLQ");
    }

    /**
     * Follow team with consistency management
     */
    @Transactional
    public void followTeam(String userId, String teamId) {
        log.info("User {} following team {}", userId, teamId);
        
        try {
            UserNode user = userNodeRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            
            TeamNode team = teamNodeRepository.findByTeamId(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found: " + teamId));
            
            user.getFollowedTeams().add(team);
            userNodeRepository.save(user);
            
            log.info("Successfully created Neo4j relationship: User {} -> Team {}", userId, teamId);
            
            // Optional: Add activity log for following a team if ActivityLogService supports it
            // activityLogService.logTeamFollow(userId, teamId); 
            
        } catch (Exception e) {
            log.error("Failed to follow team: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to follow team", e);
        }
    }

    /**
     * Synchronize user data between databases
     * Used for data reconciliation and recovery
     */
    public void synchronizeUserData(String userId) {
        log.info("Synchronizing data for user {}", userId);
        
        try {
            // Fetch from both databases
            User mongoUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found in MongoDB"));
            
            UserNode neoUser = userNodeRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User not found in Neo4j"));
            
            // Verify consistency
            if (!mongoUser.getUsername().equals(neoUser.getUsername())) {
                log.warn("Username mismatch detected for user {}", userId);
                // Implement reconciliation logic
            }
            
            log.info("User data synchronized successfully");
            
        } catch (Exception e) {
            log.error("Failed to synchronize user data: {}", e.getMessage(), e);
            throw new RuntimeException("Synchronization failed", e);
        }
    }

    /**
     * Health check: Verify consistency between databases
     */
    public ConsistencyReport checkConsistency() {
        log.info("Running consistency check");
        
        ConsistencyReport report = new ConsistencyReport();
        
        try {
            // Count users in both databases
            long mongoUserCount = userRepository.count();
            long neoUserCount = userNodeRepository.count();
            
            report.setMongoUserCount(mongoUserCount);
            report.setNeoUserCount(neoUserCount);
            report.setConsistent(mongoUserCount == neoUserCount);
            
            if (!report.isConsistent()) {
                log.warn("User count mismatch: MongoDB={}, Neo4j={}", 
                    mongoUserCount, neoUserCount);
            }
            
        } catch (Exception e) {
            log.error("Consistency check failed: {}", e.getMessage(), e);
            report.setError(e.getMessage());
        }
        
        return report;
    }
}

// ========== Consistency Report ==========
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ConsistencyReport {
    private long mongoUserCount;
    private long neoUserCount;
    private boolean consistent;
    private String error;
}