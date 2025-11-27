package com.gamesense.repository.neo4j;

import com.gamesense.model.neo4j.*;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


// ========== Game Node Repository ==========
@Repository
public interface GameNodeRepository extends Neo4jRepository<GameNode, Long> {
    
    Optional<GameNode> findByGameId(String gameId);
    
    @Query("MATCH (u:User)-[:OWNS]->(g:Game {gameId: $gameId}) RETURN count(u)")
    Long countOwners(@Param("gameId") String gameId);
    
    @Query("MATCH (g:Game) WHERE any(genre IN g.genres WHERE genre = $genre) RETURN g")
    List<GameNode> findByGenre(@Param("genre") String genre);
}
