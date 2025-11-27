package com.gamesense.service;

import com.gamesense.model.mongo.*;
import com.gamesense.model.neo4j.UserNode; 
import com.gamesense.repository.mongo.*;
import com.gamesense.repository.neo4j.UserNodeRepository; 
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserNodeRepository userNodeRepository; 
    private final DeadLetterQueueService deadLetterQueueService;

    public Optional<User> getUserById(String id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public User createUser(User user) {
        // Check if username/email already exists
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username already exists: " + user.getUsername());
        }
        
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already exists: " + user.getEmail());
        }
        
        user.setCreatedAt(LocalDateTime.now());
        
        // 1. Save to MongoDB
        User saved = userRepository.save(user);
        
        // 2. Sync to Neo4j
        try {
            UserNode userNode = new UserNode();
            userNode.setUserId(saved.getId());
            userNode.setUsername(saved.getUsername());
            userNode.setCreatedAt(saved.getCreatedAt());
            userNodeRepository.save(userNode);
            log.info("Synced user to Neo4j: {}", saved.getUsername());
            
        } catch (Exception e) {
            log.error("Failed to sync user to Neo4j", e);
            
            // PRODUCTION REQUIREMENT: Handle the inconsistency
            // Since we can't rollback the Mongo write easily (separate DBs),
            // we send this to the DLQ so a background job or admin can fix the graph later.
            deadLetterQueueService.logFailedOperation(
                "CREATE_USER_GRAPH_NODE",
                saved.getId(),
                "UserNode",
                "PENDING",
                e.getMessage()
            );
        }

        log.info("Created user: {}", saved.getUsername());
        return saved;
    }

    @Transactional
    public User updateUser(String id, User user) {
        User existing = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found: " + id));
        
        existing.setBio(user.getBio());
        existing.setAvatarUrl(user.getAvatarUrl());
        
        return userRepository.save(existing);
    }
}