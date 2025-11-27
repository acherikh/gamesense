package com.gamesense.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// ========== Trending Game DTO ==========
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendingGame {
    private String gameId;
    private String title;
    private List<String> genres;
    private String coverImageUrl;
    private Long reviewCount;
    private Double avgRating;
    private Double hypeScore; // Calculated: reviewCount * avgRating
}