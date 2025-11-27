package com.gamesense.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// ========== Dashboard Stats Response ==========
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStats {
    private Long totalGames;
    private Long totalUsers;
    private Long totalReviews;
    private Long activeMatches;
    private List<TrendingGame> trendingGames;
    private List<UpcomingMatch> upcomingMatches;
}
