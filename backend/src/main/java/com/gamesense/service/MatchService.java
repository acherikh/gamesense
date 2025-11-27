package com.gamesense.service;

import com.gamesense.model.mongo.*;
import com.gamesense.repository.mongo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// ========== Match Service ==========
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchService {

    private final MatchRepository matchRepository;

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
        log.info("Created match: {} vs {}", match.getTeamAName(), match.getTeamBName());
        
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
}
