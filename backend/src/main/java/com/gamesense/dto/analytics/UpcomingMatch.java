package com.gamesense.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// ========== Upcoming Match DTO ==========
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpcomingMatch {
    private String matchId;
    private String tournamentName;
    private String teamAName;
    private String teamBName;
    private String scheduledAt;
    private String streamUrl;
}