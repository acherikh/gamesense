package com.gamesense.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Set;

@Document(collection = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String username;
    
    @Indexed(unique = true)
    private String email;
    
    private String password; // BCrypt Hash
    
    private Set<String> roles; // e.g., ["ROLE_USER", "ROLE_ADMIN"]

    private String bio;
    private String avatarUrl;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}