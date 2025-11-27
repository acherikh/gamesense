package com.gamesense.repository.mongo;

import com.gamesense.model.mongo.*;
import com.gamesense.model.mongo.MatchStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

// ========== Match Repository ==========
@Repository
public interface MatchRepository extends MongoRepository<Match, String> {
    
    List<Match> findByStatus(MatchStatus live);
    
    List<Match> findByTournamentId(String tournamentId);
    
    List<Match> findByTeamAIdOrTeamBId(String teamAId, String teamBId);
    
    @Query("{ 'status': 'LIVE', 'scheduledAt': { $lte: ?0 } }")
    List<Match> findCurrentLiveMatches(LocalDateTime now);
    
    List<Match> findByScheduledAtBetween(LocalDateTime start, LocalDateTime end);
    
    @Query("{ $or: [ { 'teamAId': ?0 }, { 'teamBId': ?0 } ], 'status': 'FINISHED' }")
    List<Match> findFinishedMatchesByTeam(String teamId);
}
