package com.gamesense.repository.mongo;

import com.gamesense.model.mongo.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// ========== Game Repository ==========
@Repository
public interface GameRepository extends MongoRepository<Game, String> {
    
    @Query("{ 'genres': { $regex: ?0, $options: 'i' } }")
    Page<Game> findByGenresContaining(String genre, Pageable pageable);    
    
    List<Game> findByReleaseDateAfter(LocalDate date);
    
    @Query("{ 'title': { $regex: ?0, $options: 'i' } }")
    List<Game> searchByTitle(String title);
    
    @Query(value = "{ $text: { $search: ?0 } }", 
           fields = "{ score: { $meta: 'textScore' } }")
    List<Game> fullTextSearch(String searchTerm);
    
    Optional<Game> findByTitleIgnoreCase(String title);
}