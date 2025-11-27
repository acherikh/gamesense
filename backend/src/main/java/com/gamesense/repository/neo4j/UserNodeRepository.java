package com.gamesense.repository.neo4j;

import com.gamesense.model.neo4j.*;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// ========== User Node Repository ==========
@Repository
public interface UserNodeRepository extends Neo4jRepository<UserNode, Long> {
    
    Optional<UserNode> findByUserId(String userId);
    
    @Query("MATCH (u:User {userId: $userId})-[:OWNS]->(g:Game) RETURN g")
    List<GameNode> findOwnedGames(@Param("userId") String userId);
    
    @Query("MATCH (u:User {userId: $userId})-[:FOLLOWS_USER]->(other:User) RETURN other")
    List<UserNode> findFollowing(@Param("userId") String userId);
    
    @Query("MATCH (u:User {userId: $userId})-[:FOLLOWS_TEAM]->(t:Team) RETURN t")
    List<TeamNode> findFollowedTeams(@Param("userId") String userId);
}
