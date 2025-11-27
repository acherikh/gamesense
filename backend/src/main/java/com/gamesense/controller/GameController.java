package com.gamesense.controller;

import com.gamesense.dto.analytics.*;
import com.gamesense.model.mongo.*;
import com.gamesense.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

// ========== Game Controller ==========
@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@Tag(name = "Games", description = "Game catalog management")
public class GameController {

    private final GameService gameService;
    private final AnalyticsAggregationService analyticsService;

    @GetMapping
    @Operation(summary = "Get all games with pagination")
    public ResponseEntity<Page<Game>> getAllGames(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(gameService.getAllGames(PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get game by ID")
    public ResponseEntity<Game> getGameById(@PathVariable String id) {
        return gameService.getGameById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    @Operation(summary = "Search games by title")
    public ResponseEntity<List<Game>> searchGames(@RequestParam String query) {
        return ResponseEntity.ok(gameService.searchGames(query));
    }

    @GetMapping("/genre/{genre}")
    @Operation(summary = "Get games by genre")
    public ResponseEntity<Page<Game>> getGamesByGenre(
            @PathVariable String genre,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
            gameService.getGamesByGenre(genre, PageRequest.of(page, size))
        );
    }

    @PostMapping
    @Operation(summary = "Create new game")
    public ResponseEntity<Game> createGame(@Valid @RequestBody Game game) {
        return ResponseEntity.ok(gameService.createGame(game));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update game")
    public ResponseEntity<Game> updateGame(
            @PathVariable String id,
            @Valid @RequestBody Game game) {
        return ResponseEntity.ok(gameService.updateGame(id, game));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete game")
    public ResponseEntity<Void> deleteGame(@PathVariable String id) {
        gameService.deleteGame(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/trending")
    @Operation(summary = "Get trending games (Hype Meter aggregation)")
    public ResponseEntity<List<TrendingGame>> getTrendingGames(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.calculateHypeMeter(days, limit));
    }
}
