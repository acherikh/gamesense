package com.gamesense.repository.mongo;

import com.gamesense.model.mongo.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;


// ========== Review Repository ==========
@Repository
public interface ReviewRepository extends MongoRepository<Review, String> {
    
    List<Review> findByGameIdOrderByTimestampDesc(String gameId);
    
    Page<Review> findByGameId(String gameId, Pageable pageable);
    
    List<Review> findByUserId(String userId);
    
    List<Review> findByGameIdAndTimestampAfter(String gameId, LocalDateTime timestamp);
    
    Long countByGameId(String gameId);
    
    @Query("{ 'gameId': ?0, 'rating': { $gte: ?1 } }")
    List<Review> findPositiveReviews(String gameId, Integer minRating);
}