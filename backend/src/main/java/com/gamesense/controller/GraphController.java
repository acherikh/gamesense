package com.gamesense.controller;

import com.gamesense.dto.analytics.*;
import com.gamesense.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.gamesense.dto.graphs.UserInfluence;

import java.util.List;

// ========== Graph Controller ==========
@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
@Tag(name = "Graph", description = "Graph-based operations")
public class GraphController {

    private final GraphService graphService;

    @GetMapping("/recommendations/{userId}")
    @Operation(summary = "Get game recommendations based on social graph")
    public ResponseEntity<List<GameRecommendationResponse>> getRecommendations(
            @PathVariable String userId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(graphService.getGameRecommendations(userId, limit));
    }

    @GetMapping("/similar-users/{userId}")
    @Operation(summary = "Find similar users")
    public ResponseEntity<List<UserSimilarityResponse>> getSimilarUsers(
            @PathVariable String userId,
            @RequestParam(defaultValue = "5") int minShared,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(
            graphService.findSimilarUsers(userId, minShared, limit)
        );
    }

    @PostMapping("/follow/{userId}/user/{targetUserId}")
    @Operation(summary = "Follow another user")
    public ResponseEntity<Void> followUser(
            @PathVariable String userId,
            @PathVariable String targetUserId) {
        graphService.followUser(userId, targetUserId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/influencers")
    @Operation(summary = "Find influential users using centrality")
    public ResponseEntity<List<UserInfluence>> getInfluentialUsers(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(graphService.findInfluentialUsers(limit));
    }

    @PostMapping("/follow/{userId}/team/{teamId}")
    @Operation(summary = "Follow a team")
    public ResponseEntity<Void> followTeam(
            @PathVariable String userId,
            @PathVariable String teamId) {
        graphService.followTeam(userId, teamId);
        return ResponseEntity.ok().build();
    }
}