package com.gamesense.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// ========== User Similarity Response ==========
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSimilarityResponse {
    private String userId;
    private String username;
    private Integer sharedGames;
    private List<String> commonGenres;
}
