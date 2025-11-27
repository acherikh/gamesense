package com.gamesense.model.neo4j;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.*;

import java.time.ZonedDateTime; 
import java.util.Set;

// ========== Game Node ==========
@Node("Game")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameNode {
    @Id
    @GeneratedValue
    private Long graphId;
    
    @Property("gameId")
    private String gameId; // Reference to MongoDB
    
    @Property("title")
    private String title;
    
    @Property("genres")
    private Set<String> genres;
    
    @Property("createdAt")
    private ZonedDateTime createdAt; 
}