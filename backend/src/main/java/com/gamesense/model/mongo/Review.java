package com.gamesense.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

// ========== Review Entity ==========
@Document(collection = "reviews")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "game_timestamp_idx", def = "{'gameId': 1, 'timestamp': -1}")
public class Review {
    @Id
    private String id;
    
    @Indexed
    private String gameId;
    
    @Indexed
    private String userId;
    
    private String content;
    
    private Integer rating; // 1-10
    
    @Indexed
    private LocalDateTime timestamp;
    
    private Double sentimentScore; // -1.0 to 1.0
    
    private Integer upvotes;
    private Integer downvotes;
    
    private String source; // "INTERNAL", "STEAM", "SCRAPED"
    
    private LocalDateTime createdAt;
}
