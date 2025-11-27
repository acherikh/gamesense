package com.gamesense.service;

import com.gamesense.model.mongo.*;
import com.gamesense.repository.mongo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// ========== Review Service ==========
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final GameService gameService;

    public Page<Review> getReviewsByGame(String gameId, Pageable pageable) {
        return reviewRepository.findByGameId(gameId, pageable);
    }

    public List<Review> getReviewsByUser(String userId) {
        return reviewRepository.findByUserId(userId);
    }

    @Transactional
    public Review createReview(Review review) {
        review.setTimestamp(LocalDateTime.now());
        review.setCreatedAt(LocalDateTime.now());
        
        review.setSentimentScore(calculateSentiment(review.getRating()));
        
        Review saved = reviewRepository.save(review);
        log.info("Created review for game: {}", review.getGameId());
        
        // Update game's average score
        updateGameAverageScore(review.getGameId());
        
        return saved;
    }

    @Transactional
    public Review updateReview(String id, Review review) {
        Review existing = reviewRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Review not found: " + id));
        
        existing.setContent(review.getContent());
        existing.setRating(review.getRating());
        existing.setSentimentScore(calculateSentiment(review.getRating()));
        
        return reviewRepository.save(existing);
    }

    @Transactional
    public void deleteReview(String id) {
        Review review = reviewRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Review not found: " + id));
        
        String gameId = review.getGameId();
        reviewRepository.deleteById(id);
        
        // Recalculate game score
        updateGameAverageScore(gameId);
        
        log.info("Deleted review: {}", id);
    }

    private void updateGameAverageScore(String gameId) {
        List<Review> reviews = reviewRepository.findByGameIdOrderByTimestampDesc(gameId);
        
        if (!reviews.isEmpty()) {
            double avgRating = reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);
            
            gameService.updateGameScore(gameId, avgRating, reviews.size());
        }
    }

    private Double calculateSentiment(Integer rating) {
        // Simple sentiment mapping: 1-10 scale to -1.0 to 1.0
        return (rating - 5.5) / 4.5;
    }
}
