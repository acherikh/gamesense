package com.gamesense.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

// ========== Match Entity ==========
@Document(collection = "matches")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Match {
    @Id
    private String id;
    
    @Indexed
    private String tournamentId;
    
    private String tournamentName;
    
    @Indexed
    private String teamAId;
    private String teamAName;
    
    @Indexed
    private String teamBId;
    private String teamBName;
    
    private Integer scoreA;
    private Integer scoreB;
    
    @Indexed
    private MatchStatus status;
    
    @Indexed
    private String winnerId;
    
    private String gameTitle; // e.g., "League of Legends", "CS:GO"
    
    @Indexed
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    
    private String streamUrl;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
