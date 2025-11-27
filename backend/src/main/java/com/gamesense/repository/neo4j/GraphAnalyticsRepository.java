package com.gamesense.repository.neo4j;

import com.gamesense.dto.graphs.SimilarUser;
import com.gamesense.dto.graphs.UserInfluence;
import com.gamesense.model.neo4j.UserNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

// ========== Advanced Graph Queries Repository ==========
@Repository
public interface GraphAnalyticsRepository extends Neo4jRepository<UserNode, Long> {
    
    /**
     * Domain-Specific Query: Social Game Recommendation
     * Find games played by users who follow the same teams as the given user
     */
    @Query("""
        MATCH (me:User {userId: $userId})-[:FOLLOWS_TEAM]->(t:Team)<-[:FOLLOWS_TEAM]-(other:User)
        WHERE other.userId <> $userId
        MATCH (other)-[owns:OWNS]->(g:Game)
        WHERE owns.status IN ['PLAYING', 'COMPLETED']
        AND NOT EXISTS((me)-[:OWNS]->(g)) 
        WITH g, count(DISTINCT other) as commonUsers
        RETURN g.gameId as gameId, g.title as title, commonUsers
        ORDER BY commonUsers DESC
        LIMIT $limit
    """)
    List<GameRecommendation> recommendGamesByTeamAffinity(
        @Param("userId") String userId,
        @Param("limit") int limit
    );
    
    /**
     * Graph-Centric Query: User Influence Analysis
     * Calculate betweenness centrality to find connector users
     */
    @Query("""
        CALL gds.betweenness.stream({
            nodeProjection: 'User',
            relationshipProjection: 'FOLLOWS_USER'
        })
        YIELD nodeId, score
        MATCH (u:User) WHERE id(u) = nodeId
        RETURN u.userId as userId, u.username as username, score
        ORDER BY score DESC
        LIMIT $limit
    """)
    List<UserInfluence> findInfluentialUsers(@Param("limit") int limit);
    
    /**
     * Find users in the same gaming community
     */
    @Query("""
        MATCH (u1:User {userId: $userId})-[:OWNS]->(g:Game)<-[:OWNS]-(u2:User)
        WHERE u1 <> u2
        WITH u2, count(g) as sharedGames
        WHERE sharedGames >= $minShared
        RETURN u2.userId as userId, u2.username as username, sharedGames
        ORDER BY sharedGames DESC
        LIMIT $limit
    """)
    List<SimilarUser> findSimilarGamers(
        @Param("userId") String userId,
        @Param("minShared") int minShared,
        @Param("limit") int limit
    );
    
    /**
     * Multi-hop relationship: Find games through team connections
     */
    @Query("""
        MATCH (me:User {userId: $userId})-[:FOLLOWS_TEAM]->(t:Team)
        MATCH (t)<-[:FOLLOWS_TEAM]-(other:User)-[:FOLLOWS_USER*1..2]-(friend:User)
        MATCH (friend)-[:OWNS {status: 'PLAYING'}]->(g:Game)
        WHERE NOT EXISTS((me)-[:OWNS]->(g))
        WITH g, count(DISTINCT friend) as connections
        RETURN g.gameId as gameId, g.title as title, connections
        ORDER BY connections DESC
        LIMIT $limit
    """)
    List<GameRecommendation> findGamesViaExtendedNetwork(
        @Param("userId") String userId,
        @Param("limit") int limit
    );
}