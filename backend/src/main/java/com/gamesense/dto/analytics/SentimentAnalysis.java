package com.gamesense.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// ========== Sentiment Analysis DTO ==========
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentAnalysis {
    private String gameId;
    private String title;
    private Long totalReviews;
    private Double avgSentiment; // -1.0 to 1.0
    private Double avgRating;    // 1-10
}
