package com.gamesense.model.neo4j;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

// ========== Team Node ==========
@Node("Team")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamNode {
    @Id
    @GeneratedValue
    private Long graphId;
    
    @Property("teamId")
    private String teamId;
    
    @Property("name")
    private String name;
    
    @Property("region")
    private String region;
    
    @Property("gameTitle")
    private String gameTitle; // "League of Legends", "CS:GO"
    
    @Relationship(type = "COMPETES_IN", direction = Relationship.Direction.OUTGOING)
    private Set<TournamentNode> tournaments = new HashSet<>();
    
    @Relationship(type = "HAS_PLAYER", direction = Relationship.Direction.OUTGOING)
    private Set<ProPlayerNode> players = new HashSet<>();
    
    @Property("totalWinnings")
    private Double totalWinnings;
    
    @Property("createdAt")
    private LocalDateTime createdAt;
}