package com.gamesense.model.neo4j;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.*;

import java.time.LocalDateTime;

// ========== Tournament Node ==========
@Node("Tournament")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentNode {
    @Id
    @GeneratedValue
    private Long graphId;
    
    @Property("tournamentId")
    private String tournamentId;
    
    @Property("name")
    private String name;
    
    @Property("gameTitle")
    private String gameTitle;
    
    @Property("startDate")
    private LocalDateTime startDate;
    
    @Property("endDate")
    private LocalDateTime endDate;
    
    @Property("prizePool")
    private Double prizePool;
    
    @Property("status")
    private String status; // UPCOMING, ONGOING, FINISHED
    
    @Property("createdAt")
    private LocalDateTime createdAt;
}
