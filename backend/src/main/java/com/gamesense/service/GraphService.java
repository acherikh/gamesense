package com.gamesense.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

import com.gamesense.model.neo4j.UserNode;
import com.gamesense.model.neo4j.TeamNode;

import com.gamesense.repository.neo4j.UserNodeRepository;
import com.gamesense.repository.neo4j.TeamNodeRepository;
import com.gamesense.repository.neo4j.GraphAnalyticsRepository;

import com.gamesense.dto.graphs.SimilarUser;
import com.gamesense.dto.graphs.UserInfluence;
import com.gamesense.dto.analytics.GameRecommendationResponse;
import com.gamesense.dto.analytics.UserSimilarityResponse;

import java.util.List;

// ========== Graph Service ==========
@Service
@RequiredArgsConstructor
@Slf4j
public class GraphService {

    private final Driver neo4jDriver; 
    private final GraphAnalyticsRepository graphAnalyticsRepository;
    private final UserNodeRepository userNodeRepository;
    private final TeamNodeRepository teamNodeRepository;

    public List<GameRecommendationResponse> getGameRecommendations(String userId, int limit) {
        log.info("Getting game recommendations for user: {}", userId);
        
        // Use Driver directly to avoid OGM Mapping issues
        try (Session session = neo4jDriver.session()) {
            return session.run("""
                MATCH (me:User {userId: $userId})-[:FOLLOWS_TEAM]->(t:Team)<-[:FOLLOWS_TEAM]-(other:User)
                WHERE other.userId <> $userId
                MATCH (other)-[owns:OWNS]->(g:Game)
                WHERE owns.status IN ['PLAYING', 'COMPLETED']
                AND NOT EXISTS((me)-[:OWNS]->(g))
                WITH g, count(DISTINCT other) as commonUsers
                RETURN g.gameId as gameId, g.title as title, commonUsers
                ORDER BY commonUsers DESC
                LIMIT $limit
            """, java.util.Map.of("userId", userId, "limit", limit))
            .list(record -> GameRecommendationResponse.builder()
                .gameId(record.get("gameId").asString())
                .title(record.get("title").asString())
                .score((double) record.get("commonUsers").asInt())
                .reason("Played by " + record.get("commonUsers").asInt() + " users who follow your teams")
                .build());
        } catch (Exception ex) {
            log.warn("Failed to fetch recommendations from Neo4j: {}", ex.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    public List<UserSimilarityResponse> findSimilarUsers(String userId, int minShared, int limit) {
        log.info("Finding similar users for: {}", userId);
        // First try repository query
        try {
            List<SimilarUser> similar = graphAnalyticsRepository.findSimilarGamers(userId, minShared, limit);
            if (similar != null && !similar.isEmpty()) {
                return similar.stream()
                    .map(user -> UserSimilarityResponse.builder()
                        .userId(user.getUserId())
                        .username(user.getUsername())
                        .sharedGames(user.getSharedGames())
                        .build())
                    .toList();
            }
        } catch (Exception ex) {
            log.warn("Repository similar-users query failed: {}", ex.getMessage());
        }

        // Fallback: use raw Cypher via the Driver to avoid mapping issues
        try (Session session = neo4jDriver.session()) {
            var records = session.run(
                """
                MATCH (u1:User {userId: $userId})-[:OWNS]->(g:Game)<-[:OWNS]-(u2:User)
                WHERE u1.userId <> u2.userId
                WITH u2, count(g) as sharedGames
                WHERE sharedGames >= $minShared
                RETURN u2.userId as userId, u2.username as username, sharedGames
                ORDER BY sharedGames DESC
                LIMIT $limit
                """,
                java.util.Map.of("userId", userId, "minShared", minShared, "limit", limit)
            ).list();

            return records.stream()
                .map(r -> UserSimilarityResponse.builder()
                    .userId(r.get("userId").asString())
                    .username(r.get("username").asString())
                    .sharedGames(r.get("sharedGames").asInt())
                    .build())
                .toList();
        } catch (Exception ex) {
            log.warn("Failed to fetch similar users via raw Cypher: {}", ex.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    public List<UserInfluence> findInfluentialUsers(int limit) {
        log.info("Finding influential users");
        // Try GDS-backed repository first
        try {
            List<UserInfluence> gdsResults = graphAnalyticsRepository.findInfluentialUsers(limit);
            if (gdsResults != null && !gdsResults.isEmpty()) {
                return gdsResults;
            }
        } catch (Exception ex) {
            log.warn("GDS influencer query failed or unavailable: {}", ex.getMessage());
        }

        // Fallback: compute simple degree centrality (followers count) via raw Cypher
        try (Session session = neo4jDriver.session()) {
            var records = session.run(
                """
                MATCH (u:User)
                OPTIONAL MATCH (u)<-[:FOLLOWS_USER]-(f:User)
                WITH u, count(f) as followers
                RETURN u.userId as userId, u.username as username, toFloat(followers) as score
                ORDER BY score DESC
                LIMIT $limit
                """,
                java.util.Map.of("limit", limit)
            ).list();

            java.util.List<UserInfluence> out = new java.util.ArrayList<>();
            for (var r : records) {
                final String uid = r.get("userId").isNull() ? null : r.get("userId").asString();
                final String uname = r.get("username").isNull() ? null : r.get("username").asString();
                final Double score = r.get("score").isNull() ? 0.0 : r.get("score").asDouble();

                out.add(new UserInfluence() {
                    public String getUserId() { return uid; }
                    public String getUsername() { return uname; }
                    public Double getScore() { return score; }
                });
            }
            return out;
        } catch (Exception ex) {
            log.error("Failed to compute influencer fallback: {}", ex.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    
    @Transactional
    public void followUser(String userId, String targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new IllegalArgumentException("Users cannot follow themselves");
        }

        UserNode user = userNodeRepository.findByUserId(userId)
            .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            
        UserNode target = userNodeRepository.findByUserId(targetUserId)
            .orElseThrow(() -> new RuntimeException("Target user not found: " + targetUserId));
            
        user.getFollowing().add(target);
        userNodeRepository.save(user);
        
        log.info("User {} is now following User {}", userId, targetUserId);
    }

    @Transactional
    public void followTeam(String userId, String teamId) {
        log.info("User {} following team {}", userId, teamId);
        
        UserNode user = userNodeRepository.findByUserId(userId)
            .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        
        TeamNode team = teamNodeRepository.findByTeamId(teamId)
            .orElseThrow(() -> new RuntimeException("Team not found: " + teamId));
        
        user.getFollowedTeams().add(team);
        userNodeRepository.save(user);
        
        log.info("Successfully created follow relationship");
    }
}