package com.gamesense.repository.neo4j;

import com.gamesense.model.neo4j.*;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// ========== ProPlayer Node Repository ==========
@Repository
public interface ProPlayerNodeRepository extends Neo4jRepository<ProPlayerNode, Long> {
    
    Optional<ProPlayerNode> findByPlayerId(String playerId);
    
    @Query("MATCH (p:ProPlayer)-[:PLAYS_FOR]->(t:Team {teamId: $teamId}) RETURN p")
    List<ProPlayerNode> findPlayersByTeam(@Param("teamId") String teamId);
}
