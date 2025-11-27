package com.gamesense.service;

import com.gamesense.model.mongo.*;
import com.gamesense.repository.mongo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gamesense.model.neo4j.GameNode;
import com.gamesense.repository.neo4j.GameNodeRepository;
import java.time.ZoneId;
import java.util.HashSet;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// ========== Game Service ==========
@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {

    private final GameRepository gameRepository;
    private final GameNodeRepository gameNodeRepository;
    
    @Cacheable(value = "games", key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    public Page<Game> getAllGames(Pageable pageable) {
        log.info("Fetching games page: {}", pageable.getPageNumber());
        return gameRepository.findAll(pageable);
    }

    @Cacheable(value = "game", key = "#id")
    public Optional<Game> getGameById(String id) {
        return gameRepository.findById(id);
    }

    public List<Game> searchGames(String query) {
        log.info("Searching games: {}", query);
        return gameRepository.fullTextSearch(query);
    }

    public Page<Game> getGamesByGenre(String genre, Pageable pageable) {
        return gameRepository.findByGenresContaining(genre, pageable);
    }

    @Transactional
    public Game createGame(Game game) {
        game.setCreatedAt(LocalDateTime.now());
        game.setUpdatedAt(LocalDateTime.now());
        
        Game saved = gameRepository.save(game);
        
        // Sync to Neo4j
        try {
            GameNode gameNode = GameNode.builder()
                .gameId(saved.getId())
                .title(saved.getTitle())
                .genres(saved.getGenres() != null ? new HashSet<>(saved.getGenres()) : new HashSet<>())
                .createdAt(saved.getCreatedAt().atZone(ZoneId.systemDefault()))
                .build();
            gameNodeRepository.save(gameNode);
        } catch (Exception e) {
            log.error("Failed to sync created game to Neo4j: {}", e.getMessage());
        }

        log.info("Created game: {} (ID: {})", saved.getTitle(), saved.getId());
        return saved;
    }

    @Transactional
    public Game updateGame(String id, Game game) {
        Game existing = gameRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Game not found: " + id));
        
        // Update fields
        existing.setTitle(game.getTitle());
        existing.setDescription(game.getDescription());
        existing.setGenres(game.getGenres());
        existing.setDeveloper(game.getDeveloper());
        existing.setPublisher(game.getPublisher());
        existing.setUpdatedAt(LocalDateTime.now());
        
        Game saved = gameRepository.save(existing);

        // Sync to Neo4j
        gameNodeRepository.findByGameId(id).ifPresent(node -> {
            node.setTitle(saved.getTitle());
            node.setGenres(saved.getGenres() != null ? new HashSet<>(saved.getGenres()) : new HashSet<>());
            gameNodeRepository.save(node);
        });

        return saved;
    }

    @Transactional
    public void deleteGame(String id) {
        gameNodeRepository.findByGameId(id).ifPresent(gameNodeRepository::delete);
        gameRepository.deleteById(id);
        log.info("Deleted game: {}", id);
    }

    public void updateGameScore(String gameId, Double newAvgScore, Integer totalReviews) {
        gameRepository.findById(gameId).ifPresent(game -> {
            game.setAvgScore(newAvgScore);
            game.setTotalReviews(totalReviews);
            game.setUpdatedAt(LocalDateTime.now());
            gameRepository.save(game);
        });
    }
}
