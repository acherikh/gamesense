package com.gamesense.controller;

import com.gamesense.dto.analytics.*;
import com.gamesense.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// ========== Analytics Controller ==========
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Analytics and statistics")
public class AnalyticsController {

    private final AnalyticsAggregationService analyticsService;
    private final DeadLetterQueueService dlqService;

    @GetMapping("/admin/dlq-monitor")
    public ResponseEntity<List<Map>> getSystemHealth() {
        List<Map> failures = dlqService.getPendingFailures();
        return ResponseEntity.ok(failures);
    }

    @GetMapping("/trending")
    @Operation(summary = "Hype Meter - Trending games by velocity")
    public ResponseEntity<List<TrendingGame>> getTrendingGames(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.calculateHypeMeter(days, limit));
    }

    @GetMapping("/genres/trends")
    @Operation(summary = "Genre dominance over time")
    public ResponseEntity<List<GenreTrend>> getGenreTrends() {
        return ResponseEntity.ok(analyticsService.analyzeGenreDominance());
    }

    @GetMapping("/teams/performance")
    @Operation(summary = "Team win rates")
    public ResponseEntity<List<TeamPerformance>> getTeamPerformance(
            @RequestParam(required = false) String gameTitle) {
        return ResponseEntity.ok(analyticsService.calculateTeamWinRates(gameTitle));
    }

    @GetMapping("/sentiment")
    @Operation(summary = "Sentiment analysis by game")
    public ResponseEntity<List<SentimentAnalysis>> getSentimentAnalysis(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(analyticsService.analyzeSentimentByGame(limit));
    }
}
