package com.gamesense.repository.neo4j;

import com.gamesense.model.neo4j.*;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// ========== Tournament Node Repository ==========
@Repository
public interface TournamentNodeRepository extends Neo4jRepository<TournamentNode, Long> {
    
    Optional<TournamentNode> findByTournamentId(String tournamentId);
    
    List<TournamentNode> findByGameTitle(String gameTitle);
    
    @Query("MATCH (tour:Tournament) WHERE tour.status = $status RETURN tour ORDER BY tour.startDate DESC")
    List<TournamentNode> findByStatus(@Param("status") String status);
}
