package com.gamesense.controller;

import com.gamesense.model.mongo.*;
import com.gamesense.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

// ========== Match Controller ==========
@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
@Tag(name = "Matches", description = "Esports match management")
public class MatchController {

    private final MatchService matchService;

    @GetMapping("/live")
    @Operation(summary = "Get live matches")
    public ResponseEntity<List<Match>> getLiveMatches() {
        return ResponseEntity.ok(matchService.getLiveMatches());
    }

    @GetMapping("/upcoming")
    @Operation(summary = "Get upcoming matches")
    public ResponseEntity<List<Match>> getUpcomingMatches(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(matchService.getUpcomingMatches(limit));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get match by ID")
    public ResponseEntity<Match> getMatchById(@PathVariable String id) {
        return matchService.getMatchById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tournament/{tournamentId}")
    @Operation(summary = "Get matches by tournament")
    public ResponseEntity<List<Match>> getMatchesByTournament(
            @PathVariable String tournamentId) {
        return ResponseEntity.ok(matchService.getMatchesByTournament(tournamentId));
    }

    @GetMapping("/team/{teamId}")
    @Operation(summary = "Get matches by team")
    public ResponseEntity<List<Match>> getMatchesByTeam(@PathVariable String teamId) {
        return ResponseEntity.ok(matchService.getMatchesByTeam(teamId));
    }

    @PostMapping
    @Operation(summary = "Create match")
    public ResponseEntity<Match> createMatch(@Valid @RequestBody Match match) {
        return ResponseEntity.ok(matchService.createMatch(match));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update match")
    public ResponseEntity<Match> updateMatch(
            @PathVariable String id,
            @Valid @RequestBody Match match) {
        return ResponseEntity.ok(matchService.updateMatch(id, match));
    }
}
