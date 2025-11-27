package com.gamesense.model.neo4j;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

// ========== User Node ==========
@Node("User")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserNode {
    @Id
    @GeneratedValue
    private Long graphId;
    
    @Property("userId")
    private String userId; // Reference to MongoDB
    
    @Property("username")
    private String username;
    
    @Relationship(type = "OWNS", direction = Relationship.Direction.OUTGOING)
    private Set<GameOwnership> ownedGames = new HashSet<>();
    
    @Relationship(type = "FOLLOWS_USER", direction = Relationship.Direction.OUTGOING)
    private Set<UserNode> following = new HashSet<>();
    
    @Relationship(type = "FOLLOWS_TEAM", direction = Relationship.Direction.OUTGOING)
    private Set<TeamNode> followedTeams = new HashSet<>();
    
    @Property("createdAt")
    private LocalDateTime createdAt;
}