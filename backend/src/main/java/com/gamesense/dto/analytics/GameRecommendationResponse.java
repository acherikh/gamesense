package com.gamesense.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// ========== Game Recommendation Response ==========
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameRecommendationResponse {
    private String gameId;
    private String title;
    private List<String> genres;
    private Double score;
    private String reason;
}
