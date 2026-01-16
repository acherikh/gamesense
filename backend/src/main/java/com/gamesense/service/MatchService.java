package com.gamesense.service;

import com.gamesense.model.mongo.*;
import com.gamesense.model.neo4j.TeamNode; 
import com.gamesense.repository.mongo.*;
import com.gamesense.repository.neo4j.TeamNodeRepository; 
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchService {

    private final MatchRepository matchRepository;
    private final TeamNodeRepository teamNodeRepository; // Inject Neo4j 

    public List<Match> getLiveMatches() {
        return matchRepository.findByStatus(MatchStatus.LIVE);
    }

    public List<Match> getUpcomingMatches(int limit) {
        return matchRepository.findByStatus(MatchStatus.SCHEDULED)
            .stream()
            .limit(limit)
            .toList();
    }

    public Optional<Match> getMatchById(String id) {
        return matchRepository.findById(id);
    }

    public List<Match> getMatchesByTournament(String tournamentId) {
        return matchRepository.findByTournamentId(tournamentId);
    }

    public List<Match> getMatchesByTeam(String teamId) {
        return matchRepository.findByTeamAIdOrTeamBId(teamId, teamId);
    }

    @Transactional
    public Match createMatch(Match match) {
        match.setCreatedAt(LocalDateTime.now());
        match.setUpdatedAt(LocalDateTime.now());
        
        Match saved = matchRepository.save(match);
        
        // Sync Teams to Neo4j immediately
        syncTeamToGraph(saved.getTeamAId(), saved.getTeamAName(), saved.getGameTitle());
        syncTeamToGraph(saved.getTeamBId(), saved.getTeamBName(), saved.getGameTitle());
        
        log.info("Created match and synced teams: {} vs {}", match.getTeamAName(), match.getTeamBName());
        
        return saved;
    }

    @Transactional
    public Match updateMatch(String id, Match match) {
        Match existing = matchRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Match not found: " + id));
        
        existing.setScoreA(match.getScoreA());
        existing.setScoreB(match.getScoreB());
        existing.setStatus(match.getStatus());
        existing.setWinnerId(match.getWinnerId());
        existing.setUpdatedAt(LocalDateTime.now());
        
        if (match.getStatus() == MatchStatus.LIVE && existing.getStartedAt() == null) {
            existing.setStartedAt(LocalDateTime.now());
        }
        
        if (match.getStatus() == MatchStatus.FINISHED && existing.getFinishedAt() == null) {
            existing.setFinishedAt(LocalDateTime.now());
        }
        
        return matchRepository.save(existing);
    }

    // Helper to sync teams safely
    private void syncTeamToGraph(String teamId, String name, String gameTitle) {
        try {
            if (teamId != null) {
                TeamNode teamNode = teamNodeRepository.findByTeamId(teamId)
                    .orElse(new TeamNode());
                
                teamNode.setTeamId(teamId);
                teamNode.setName(name);
                teamNode.setGameTitle(gameTitle);
                teamNodeRepository.save(teamNode);
            }
        } catch (Exception e) {
            log.error("Failed to sync team to Neo4j: {}", e.getMessage());
            // Non-blocking error
        }
    }
}