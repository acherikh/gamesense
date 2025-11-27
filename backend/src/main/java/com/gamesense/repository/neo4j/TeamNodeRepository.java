package com.gamesense.repository.neo4j;

import com.gamesense.model.neo4j.*;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


// ========== Team Node Repository ==========
@Repository
public interface TeamNodeRepository extends Neo4jRepository<TeamNode, Long> {
    
    Optional<TeamNode> findByTeamId(String teamId);
    
    List<TeamNode> findByGameTitle(String gameTitle);
    
    @Query("MATCH (t:Team)-[:COMPETES_IN]->(tour:Tournament {tournamentId: $tournamentId}) RETURN t")
    List<TeamNode> findTeamsInTournament(@Param("tournamentId") String tournamentId);
    
    @Query("MATCH (u:User)-[:FOLLOWS_TEAM]->(t:Team {teamId: $teamId}) RETURN count(u)")
    Long countFollowers(@Param("teamId") String teamId);
}
