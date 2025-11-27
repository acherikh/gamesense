package com.gamesense.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// ========== Genre Trend DTO ==========
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenreTrend {
    private Integer year;
    private String dominantGenre;
    private Long gameCount;
    private List<GenreBreakdown> genreBreakdown;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenreBreakdown {
        private String genre;
        private Long count;
    }
}
