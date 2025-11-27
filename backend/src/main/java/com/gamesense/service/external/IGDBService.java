package com.gamesense.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamesense.model.mongo.Game;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * IGDB (Internet Game Database) API Integration
 * 
 * Provides real game data including:
 * - Game metadata (title, description, release date)
 * - Genres and platforms
 * - Cover images
 * - Developer/Publisher information
 * 
 * API Documentation: https://api-docs.igdb.com/
 */
@Service
@Slf4j
public class IGDBService {

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String accessToken;
    private Long tokenExpiresAt;

    public IGDBService(
            @Value("${igdb.api-url}") String apiUrl,
            @Value("${igdb.client-id}") String clientId,
            @Value("${igdb.client-secret}") String clientSecret) {
        
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        
        this.webClient = WebClient.builder()
            .baseUrl(apiUrl)
            .build();
        
        // Initialize access token
        refreshAccessToken();
    }

    /**
     * Authenticate with Twitch (IGDB uses Twitch OAuth)
     */
    private void refreshAccessToken() {
        log.info("Refreshing IGDB access token");
        
        try {
            WebClient twitchClient = WebClient.builder()
                .baseUrl("https://id.twitch.tv/oauth2")
                .build();
            
            String response = twitchClient.post()
                .uri(uriBuilder -> uriBuilder
                    .path("/token")
                    .queryParam("client_id", clientId)
                    .queryParam("client_secret", clientSecret)
                    .queryParam("grant_type", "client_credentials")
                    .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            this.accessToken = extractAccessToken(response);
            this.tokenExpiresAt = System.currentTimeMillis() + (5184000 * 1000); // ~60 days
            
            log.info("Access token refreshed successfully");
            
        } catch (Exception e) {
            // CHANGE: Log the error but DO NOT throw the exception
            log.error("Failed to refresh access token (Application will start without IGDB): {}", e.getMessage());
            // throw new RuntimeException("IGDB authentication failed", e); <--- REMOVE THIS LINE
        }
    }

    
    private String extractAccessToken(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            if (root.has("access_token")) {
                return root.get("access_token").asText();
            }
            throw new RuntimeException("Token response missing 'access_token' field");
        } catch (Exception e) {
            log.error("Failed to parse auth token from IGDB/Twitch response: {}", response);
            throw new RuntimeException("Failed to parse auth token", e);
        }
    }

    /**
     * Search games by name
     */
    public List<Game> searchGames(String query, int limit) {
        log.info("Searching games: {}", query);
        
        ensureTokenValid();
        
        String body = String.format(
            "search \"%s\"; fields name,summary,cover.url,genres.name,platforms.name,first_release_date,involved_companies.company.name; limit %d;",
            query, limit
        );
        
        try {
            JsonNode response = webClient.post()
                .uri("/games")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("Client-ID", clientId)
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            
            return parseGamesResponse(response);
            
        } catch (Exception e) {
            log.error("Failed to search games: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Fetch popular games
     */
    public List<Game> fetchPopularGames(int limit) {
        log.info("Fetching popular games");
        
        ensureTokenValid();
        
        String body = String.format(
            "fields name,summary,cover.url,genres.name,platforms.name,first_release_date,rating,involved_companies.company.name; " +
            "where rating > 80 & first_release_date > %d; " +
            "sort rating desc; limit %d;",
            getTimestampYearsAgo(2),
            limit
        );
        
        try {
            JsonNode response = webClient.post()
                .uri("/games")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("Client-ID", clientId)
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            
            return parseGamesResponse(response);
            
        } catch (Exception e) {
            log.error("Failed to fetch popular games: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Fetch games by genre
     */
    public List<Game> fetchGamesByGenre(String genreName, int limit) {
        log.info("Fetching games by genre: {}", genreName);
        
        ensureTokenValid();
        
        // First, get genre ID
        String genreQuery = String.format(
            "search \"%s\"; fields name; limit 1;",
            genreName
        );
        
        try {
            JsonNode genreResponse = webClient.post()
                .uri("/genres")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("Client-ID", clientId)
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(genreQuery)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            
            if (genreResponse == null || !genreResponse.isArray() || genreResponse.isEmpty()) {
                log.warn("Genre not found: {}", genreName);
                return new ArrayList<>();
            }
            
            int genreId = genreResponse.get(0).get("id").asInt();
            
            // Fetch games with this genre
            String gamesQuery = String.format(
                "fields name,summary,cover.url,genres.name,platforms.name,first_release_date,involved_companies.company.name; " +
                "where genres = [%d]; limit %d;",
                genreId, limit
            );
            
            JsonNode gamesResponse = webClient.post()
                .uri("/games")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("Client-ID", clientId)
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(gamesQuery)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            
            return parseGamesResponse(gamesResponse);
            
        } catch (Exception e) {
            log.error("Failed to fetch games by genre: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Parse IGDB API response to Game entities
     */
    private List<Game> parseGamesResponse(JsonNode response) {
        List<Game> games = new ArrayList<>();
        
        if (response == null || !response.isArray()) {
            return games;
        }
        
        for (JsonNode node : response) {
            try {
                Game game = Game.builder()
                    .title(node.has("name") ? node.get("name").asText() : "Unknown")
                    .description(node.has("summary") ? node.get("summary").asText() : "")
                    .releaseDate(parseReleaseDate(node))
                    .genres(parseGenres(node))
                    .platforms(parsePlatforms(node))
                    .developer(parseDeveloper(node))
                    .coverImageUrl(parseCoverUrl(node))
                    .avgScore(node.has("rating") ? node.get("rating").asDouble() / 10.0 : null)
                    .totalReviews(0)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();
                
                games.add(game);
                
            } catch (Exception e) {
                log.warn("Failed to parse game node: {}", e.getMessage());
            }
        }
        
        log.info("Parsed {} games from IGDB response", games.size());
        return games;
    }

    private LocalDate parseReleaseDate(JsonNode node) {
        if (node.has("first_release_date")) {
            long timestamp = node.get("first_release_date").asLong();
            return Instant.ofEpochSecond(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        }
        return null;
    }

    private List<String> parseGenres(JsonNode node) {
        List<String> genres = new ArrayList<>();
        if (node.has("genres")) {
            JsonNode genresNode = node.get("genres");
            if (genresNode.isArray()) {
                for (JsonNode genre : genresNode) {
                    if (genre.has("name")) {
                        genres.add(genre.get("name").asText());
                    }
                }
            }
        }
        return genres;
    }

    private List<String> parsePlatforms(JsonNode node) {
        List<String> platforms = new ArrayList<>();
        if (node.has("platforms")) {
            JsonNode platformsNode = node.get("platforms");
            if (platformsNode.isArray()) {
                for (JsonNode platform : platformsNode) {
                    if (platform.has("name")) {
                        platforms.add(platform.get("name").asText());
                    }
                }
            }
        }
        return platforms;
    }

    private String parseDeveloper(JsonNode node) {
        if (node.has("involved_companies")) {
            JsonNode companies = node.get("involved_companies");
            if (companies.isArray() && companies.size() > 0) {
                JsonNode firstCompany = companies.get(0);
                if (firstCompany.has("company") && firstCompany.get("company").has("name")) {
                    return firstCompany.get("company").get("name").asText();
                }
            }
        }
        return "Unknown";
    }

    private String parseCoverUrl(JsonNode node) {
        if (node.has("cover") && node.get("cover").has("url")) {
            String url = node.get("cover").get("url").asText();
            // Convert to HTTPS and higher resolution
            return "https:" + url.replace("t_thumb", "t_cover_big");
        }
        return null;
    }

    private void ensureTokenValid() {
        if (tokenExpiresAt == null || System.currentTimeMillis() > tokenExpiresAt - 86400000) {
            refreshAccessToken();
        }
    }

    private long getTimestampYearsAgo(int years) {
        return Instant.now()
            .atZone(ZoneId.systemDefault())
            .minusYears(years)
            .toEpochSecond();
    }
}