package com.gamesense.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// ========== Team Performance DTO ==========
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamPerformance {
    private String teamId;
    private String teamName;
    private Long wins;
    private Long totalMatches;
    private Double winRate; // Percentage
}
