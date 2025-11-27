package com.gamesense.model.neo4j;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.*;

import java.time.LocalDateTime;

// ========== Relationship: OWNS (User -> Game) ==========
@RelationshipProperties
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameOwnership {
    @Id
    @GeneratedValue
    private Long id;
    
    @TargetNode
    private GameNode game;
    
    @Property("status")
    private GameStatus status;
    
    @Property("hoursPlayed")
    private Integer hoursPlayed;
    
    @Property("addedAt")
    private LocalDateTime addedAt;
    
    @Property("lastPlayedAt")
    private LocalDateTime lastPlayedAt;
}