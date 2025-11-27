package com.gamesense.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.gamesense.model.mongo.Match;
import com.gamesense.model.mongo.MatchStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * PandaScore API Integration for Esports Data
 * 
 * Provides real-time esports data including:
 * - Live match scores
 * - Tournament information
 * - Team data
 * - Player statistics
 * 
 * API Documentation: https://developers.pandascore.co/
 */
@Service
@Slf4j
public class PandaScoreService {

    private final WebClient webClient;
    private final String apiKey;

    public PandaScoreService(
            @Value("${pandascore.api-url}") String apiUrl,
            @Value("${pandascore.api-key}") String apiKey) {
        
        this.apiKey = apiKey;
        
        this.webClient = WebClient.builder()
            .baseUrl(apiUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + this.apiKey)
            .build();
    }

    /**
     * Fetch live matches across all games
     */
    public List<Match> fetchLiveMatches() {
        log.info("Fetching live matches from PandaScore");
        
        try {
            JsonNode response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/matches/running")
                    .queryParam("per_page", 50)
                    .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            
            return parseMatchesResponse(response, MatchStatus.LIVE);
            
        } catch (Exception e) {
            log.error("Failed to fetch live matches: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Fetch upcoming matches
     */
    public List<Match> fetchUpcomingMatches(int limit) {
        log.info("Fetching upcoming matches");
        
        try {
            JsonNode response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/matches/upcoming")
                    .queryParam("per_page", limit)
                    .queryParam("sort", "begin_at")
                    .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            
            return parseMatchesResponse(response, MatchStatus.SCHEDULED);
            
        } catch (Exception e) {
            log.error("Failed to fetch upcoming matches: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Fetch matches for specific game
     */
    public List<Match> fetchMatchesByGame(String gameSlug, int limit) {
        log.info("Fetching matches for game: {}", gameSlug);
        
        try {
            JsonNode response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/" + gameSlug + "/matches")
                    .queryParam("per_page", limit)
                    .queryParam("sort", "-begin_at")
                    .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            
            return parseMatchesResponse(response, null);
            
        } catch (Exception e) {
            log.error("Failed to fetch matches for game {}: {}", gameSlug, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Fetch matches for a specific tournament
     */
    public List<Match> fetchTournamentMatches(int tournamentId) {
        log.info("Fetching matches for tournament: {}", tournamentId);
        
        try {
            JsonNode response = webClient.get()
                .uri("/tournaments/" + tournamentId + "/matches")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            
            return parseMatchesResponse(response, null);
            
        } catch (Exception e) {
            log.error("Failed to fetch tournament matches: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Fetch team information
     */
    public JsonNode fetchTeam(int teamId) {
        log.info("Fetching team: {}", teamId);
        
        try {
            return webClient.get()
                .uri("/teams/" + teamId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            
        } catch (Exception e) {
            log.error("Failed to fetch team: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Fetch tournament information
     */
    public List<JsonNode> fetchTournaments(String gameSlug, int limit) {
        log.info("Fetching tournaments for game: {}", gameSlug);
        
        try {
            JsonNode response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/" + gameSlug + "/tournaments")
                    .queryParam("per_page", limit)
                    .queryParam("sort", "-begin_at")
                    .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            
            List<JsonNode> tournaments = new ArrayList<>();
            if (response != null && response.isArray()) {
                response.forEach(tournaments::add);
            }
            
            return tournaments;
            
        } catch (Exception e) {
            log.error("Failed to fetch tournaments: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Parse PandaScore matches response
     */
    private List<Match> parseMatchesResponse(JsonNode response, MatchStatus defaultStatus) {
        List<Match> matches = new ArrayList<>();
        
        if (response == null || !response.isArray()) {
            return matches;
        }
        
        for (JsonNode node : response) {
            try {
                Match match = parseMatch(node, defaultStatus);
                if (match != null) {
                    matches.add(match);
                }
            } catch (Exception e) {
                log.warn("Failed to parse match: {}", e.getMessage());
            }
        }
        
        log.info("Parsed {} matches from PandaScore", matches.size());
        return matches;
    }

    /**
     * Parse individual match
     */
    private Match parseMatch(JsonNode node, MatchStatus defaultStatus) {
        if (node == null) return null;
        
        // Parse teams
        String teamAId = null, teamAName = "TBD";
        String teamBId = null, teamBName = "TBD";
        
        if (node.has("opponents") && node.get("opponents").isArray()) {
            JsonNode opponents = node.get("opponents");
            
            if (opponents.size() > 0 && opponents.get(0).has("opponent")) {
                JsonNode teamA = opponents.get(0).get("opponent");
                teamAId = teamA.has("id") ? String.valueOf(teamA.get("id").asInt()) : null;
                teamAName = teamA.has("name") ? teamA.get("name").asText() : "TBD";
            }
            
            if (opponents.size() > 1 && opponents.get(1).has("opponent")) {
                JsonNode teamB = opponents.get(1).get("opponent");
                teamBId = teamB.has("id") ? String.valueOf(teamB.get("id").asInt()) : null;
                teamBName = teamB.has("name") ? teamB.get("name").asText() : "TBD";
            }
        }
        
        // Parse scores
        Integer scoreA = null, scoreB = null;
        if (node.has("results") && node.get("results").isArray()) {
            JsonNode results = node.get("results");
            if (results.size() > 0) {
                scoreA = results.get(0).has("score") ? results.get(0).get("score").asInt() : null;
            }
            if (results.size() > 1) {
                scoreB = results.get(1).has("score") ? results.get(1).get("score").asInt() : null;
            }
        }
        
        // Determine winner
        String winnerId = null;
        if (node.has("winner_id") && !node.get("winner_id").isNull()) {
            winnerId = String.valueOf(node.get("winner_id").asInt());
        }
        
        // Parse status
        MatchStatus status = defaultStatus;
        if (status == null && node.has("status")) {
            String statusStr = node.get("status").asText();
            status = mapPandaScoreStatus(statusStr);
        }
        
        // Parse tournament
        String tournamentId = null, tournamentName = "Unknown";
        if (node.has("tournament")) {
            JsonNode tournament = node.get("tournament");
            tournamentId = tournament.has("id") ? String.valueOf(tournament.get("id").asInt()) : null;
            tournamentName = tournament.has("name") ? tournament.get("name").asText() : "Unknown";
        }
        
        // Parse game title
        String gameTitle = "Unknown";
        if (node.has("videogame") && node.get("videogame").has("name")) {
            gameTitle = node.get("videogame").get("name").asText();
        }
        
        // Parse timestamps
        LocalDateTime scheduledAt = parseTimestamp(node, "scheduled_at");
        LocalDateTime startedAt = parseTimestamp(node, "begin_at");
        LocalDateTime finishedAt = parseTimestamp(node, "end_at");
        
        // Parse stream URL
        String streamUrl = null;
        if (node.has("streams_list") && node.get("streams_list").isArray()) {
            JsonNode streams = node.get("streams_list");
            if (streams.size() > 0 && streams.get(0).has("raw_url")) {
                streamUrl = streams.get(0).get("raw_url").asText();
            }
        }
        
        return Match.builder()
            .id(node.has("id") ? String.valueOf(node.get("id").asInt()) : null)
            .tournamentId(tournamentId)
            .tournamentName(tournamentName)
            .teamAId(teamAId)
            .teamAName(teamAName)
            .teamBId(teamBId)
            .teamBName(teamBName)
            .scoreA(scoreA)
            .scoreB(scoreB)
            .status(status != null ? status : MatchStatus.SCHEDULED)
            .winnerId(winnerId)
            .gameTitle(gameTitle)
            .scheduledAt(scheduledAt)
            .startedAt(startedAt)
            .finishedAt(finishedAt)
            .streamUrl(streamUrl)
            .createdAt(LocalDateTime.now())
            .build();
    }

    private MatchStatus mapPandaScoreStatus(String status) {
        return switch (status.toLowerCase()) {
            case "running" -> MatchStatus.LIVE;
            case "finished" -> MatchStatus.FINISHED;
            case "not_started" -> MatchStatus.SCHEDULED;
            case "canceled" -> MatchStatus.CANCELLED;
            case "postponed" -> MatchStatus.POSTPONED;
            default -> MatchStatus.SCHEDULED;
        };
    }

    private LocalDateTime parseTimestamp(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            String timestamp = node.get(field).asText();
            try {
                return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME);
            } catch (Exception e) {
                log.warn("Failed to parse timestamp {}: {}", field, timestamp);
            }
        }
        return null;
    }

    /**
     * Supported games on PandaScore
     */
    public enum SupportedGame {
        LOL("lol", "League of Legends"),
        CSGO("csgo", "Counter-Strike: Global Offensive"),
        DOTA2("dota2", "Dota 2"),
        VALORANT("valorant", "Valorant"),
        COD("cod-mw", "Call of Duty"),
        OVERWATCH("overwatch", "Overwatch"),
        R6SIEGE("r6siege", "Rainbow Six Siege");
        
        public final String slug;
        public final String fullName;
        
        SupportedGame(String slug, String fullName) {
            this.slug = slug;
            this.fullName = fullName;
        }
    }
}