package com.gamesense.model.neo4j;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.*;

import java.time.LocalDateTime;

// ========== ProPlayer Node ==========
@Node("ProPlayer")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProPlayerNode {
    @Id
    @GeneratedValue
    private Long graphId;
    
    @Property("playerId")
    private String playerId;
    
    @Property("gameName")
    private String gameName; // In-game name
    
    @Property("realName")
    private String realName;
    
    @Property("nationality")
    private String nationality;
    
    @Property("role")
    private String role; // Position/Role in team
    
    @Property("createdAt")
    private LocalDateTime createdAt;
}