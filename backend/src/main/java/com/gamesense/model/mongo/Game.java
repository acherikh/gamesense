package com.gamesense.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable; 
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "games")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "release_genre_idx", def = "{'releaseDate': 1, 'genres': 1}")
public class Game implements Serializable { 
    
    private static final long serialVersionUID = 1L; 

    @Id
    private String id;
    
    @TextIndexed(weight = 3)
    private String title;
    
    private LocalDate releaseDate;
    
    @Indexed
    private List<String> genres;
    
    private String developer;
    private String publisher;
    
    @TextIndexed(weight = 1)
    private String description;
    
    private Double avgScore;
    private Integer totalReviews;
    
    private String coverImageUrl;
    private List<String> platforms;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}