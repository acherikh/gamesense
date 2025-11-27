package com.gamesense.service;

import com.gamesense.dto.analytics.*;
import com.mongodb.BasicDBObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsAggregationService {

    private final MongoTemplate mongoTemplate;

    /**
     * AGGREGATION PIPELINE 1: The Hype Meter (Trending Games)
     * 
     * Pipeline Steps:
     * 1. Filter reviews from the last N days (velocity aspect)
     * 2. Group by gameId to calculate review count and average rating
     * 3. Sort by review count descending
     * 4. Lookup game details
     * 5. Project final result
     * 
     * Business Value: Identifies games experiencing spike in player activity
     */
    public List<TrendingGame> calculateHypeMeter(int days, int limit) {
        log.info("Calculating Hype Meter for last {} days", days);
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        
        // Match stage: Filter recent reviews
        MatchOperation matchRecent = match(
            Criteria.where("timestamp").gte(cutoffDate)
        );
        
        // Group stage: Calculate stats per game
        GroupOperation groupByGame = group("gameId")
            .count().as("reviewCount")
            .avg("rating").as("avgRating")
            .first("gameId").as("gameId");
        
        // Sort stage: Order by review count (velocity indicator)
        SortOperation sortByCount = sort(
            Sort.by(Sort.Direction.DESC, "reviewCount")
        );
        
        // Limit stage
        LimitOperation limitResults = limit(limit);
        
        // Lookup stage: Join with games collection
        LookupOperation lookupGame = lookup(
            "games",
            "gameId",
            "_id",
            "gameDetails"
        );
        
        // Unwind stage: Flatten game details
        UnwindOperation unwindGame = unwind("gameDetails");
        
        // Project stage: Shape final output
        ProjectionOperation projectResult = project()
            .and("gameId").as("gameId")
            .and("gameDetails.title").as("title")
            .and("gameDetails.genres").as("genres")
            .and("gameDetails.coverImageUrl").as("coverImageUrl")
            .and("reviewCount").as("reviewCount")
            .and("avgRating").as("avgRating")
            .andExpression("reviewCount * avgRating").as("hypeScore");
        
        // Build and execute aggregation
        Aggregation aggregation = newAggregation(
            matchRecent,
            groupByGame,
            sortByCount,
            limitResults,
            lookupGame,
            unwindGame,
            projectResult
        );
        
        AggregationResults<TrendingGame> results = mongoTemplate.aggregate(
            aggregation,
            "reviews",
            TrendingGame.class
        );
        
        List<TrendingGame> trendingGames = results.getMappedResults();
        log.info("Found {} trending games", trendingGames.size());
        
        return trendingGames;
    }

    /**
     * AGGREGATION PIPELINE 2: Genre Dominance Over Time
     * 
     * Pipeline Steps:
     * 1. Unwind genres array (games can have multiple genres)
     * 2. Extract year from releaseDate
     * 3. Group by year and genre, count occurrences
     * 4. Sort by year and count
     * 5. Group by year to find dominant genre
     * 
     * Business Value: Visualize industry trends and genre popularity shifts
     */
    public List<GenreTrend> analyzeGenreDominance() {
        log.info("Analyzing genre dominance over time");
        
        // Unwind genres array
        UnwindOperation unwindGenres = unwind("genres");
        
        // Project to extract year from releaseDate
        ProjectionOperation extractYear = project()
            .and("genres").as("genre")
            .andExpression("year(releaseDate)").as("year");
        
        // Match: Filter out null years
        MatchOperation filterValidYears = match(
            Criteria.where("year").ne(null)
        );
        
        // Group by year and genre
        GroupOperation groupByYearGenre = group("year", "genre")
            .count().as("gameCount")
            .first("year").as("year")
            .first("genre").as("genre");
        
        // Sort by year and count
        SortOperation sortByYearCount = sort(
            Sort.by(Sort.Direction.ASC, "year")
                .and(Sort.by(Sort.Direction.DESC, "gameCount"))
        );
        
        // Group by year to get top genre per year
        GroupOperation groupByYear = group("year")
            .first("year").as("year")
            .first("genre").as("dominantGenre")
            .first("gameCount").as("gameCount")
            .push(new BasicDBObject("genre", "$genre")
                .append("count", "$gameCount")).as("genreBreakdown");
        
        // Sort by year
        SortOperation sortByYear = sort(
            Sort.by(Sort.Direction.ASC, "_id")
        );
        
        // Project final structure
        ProjectionOperation projectResult = project()
            .and("_id").as("year")
            .and("dominantGenre").as("dominantGenre")
            .and("gameCount").as("gameCount")
            .and("genreBreakdown").as("genreBreakdown");
        
        Aggregation aggregation = newAggregation(
            unwindGenres,
            extractYear,
            filterValidYears,
            groupByYearGenre,
            sortByYearCount,
            groupByYear,
            sortByYear,
            projectResult
        );
        
        AggregationResults<GenreTrend> results = mongoTemplate.aggregate(
            aggregation,
            "games",
            GenreTrend.class
        );
        
        return results.getMappedResults();
    }

    /**
     * AGGREGATION PIPELINE 3: Esports Team Win Rates
     * 
     * Pipeline Steps:
     * 1. Filter finished matches
     * 2. Unwind to create separate records for each team
     * 3. Group by team to calculate win/loss stats
     * 4. Calculate win rate percentage
     * 5. Sort by win rate
     * 
     * Business Value: Performance metrics for esports teams
     */
    public List<TeamPerformance> calculateTeamWinRates(String gameTitle) {
        log.info("Calculating team win rates for game: {}", gameTitle);
        
        // Match finished matches
        Criteria criteria = Criteria.where("status").is("FINISHED");
        if (gameTitle != null && !gameTitle.isEmpty()) {
            criteria = criteria.and("gameTitle").is(gameTitle);
        }
        MatchOperation matchFinished = match(criteria);
        
        // Project to create team records
        ProjectionOperation projectTeamA = project()
            .and("teamAId").as("teamId")
            .and("teamAName").as("teamName")
            .and("winnerId").as("winnerId")
            .and(ConditionalOperators.when(
                ComparisonOperators.valueOf("teamAId").equalTo("winnerId") 
            ).then(1).otherwise(0)).as("isWin");
        
        ProjectionOperation projectTeamB = project()
            .and("teamBId").as("teamId")
            .and("teamBName").as("teamName")
            .and("winnerId").as("winnerId")
            .and(ConditionalOperators.when(
                ComparisonOperators.valueOf("teamBId").equalTo("winnerId")
            ).then(1).otherwise(0)).as("isWin");
        
        // We need to union both projections - using facet
        FacetOperation facetTeams = facet()
            .and(projectTeamA).as("teamAMatches")
            .and(projectTeamB).as("teamBMatches");
        
        // Project to merge arrays
        ProjectionOperation mergeArrays = project()
            .and(ArrayOperators.ConcatArrays.arrayOf("teamAMatches")
                .concat("teamBMatches"))
            .as("allMatches");
        
        // Unwind combined matches
        UnwindOperation unwindMatches = unwind("allMatches");
        
        // Replace root
        ReplaceRootOperation replaceRoot = replaceRoot("allMatches");
        
        // Group by team
        GroupOperation groupByTeam = group("teamId")
            .first("teamName").as("teamName")
            .sum("isWin").as("wins")
            .count().as("totalMatches");
        
        // Project to calculate win rate
        ProjectionOperation calculateWinRate = project()
            .and("_id").as("teamId")
            .and("teamName").as("teamName")
            .and("wins").as("wins")
            .and("totalMatches").as("totalMatches")
            .andExpression("(wins / totalMatches) * 100").as("winRate");
        
        // Sort by win rate
        SortOperation sortByWinRate = sort(
            Sort.by(Sort.Direction.DESC, "winRate")
        );
        
        Aggregation aggregation = newAggregation(
            matchFinished,
            facetTeams,
            mergeArrays,
            unwindMatches,
            replaceRoot,
            groupByTeam,
            calculateWinRate,
            sortByWinRate
        );
        
        AggregationResults<TeamPerformance> results = mongoTemplate.aggregate(
            aggregation,
            "matches",
            TeamPerformance.class
        );
        
        return results.getMappedResults();
    }
    
    /**
     * Additional: Review Sentiment Analysis by Game
     */
    public List<SentimentAnalysis> analyzeSentimentByGame(int limit) {
        log.info("Analyzing sentiment distribution by game");
        
        GroupOperation groupByGame = group("gameId")
            .count().as("totalReviews")
            .avg("sentimentScore").as("avgSentiment")
            .avg("rating").as("avgRating")
            .first("gameId").as("gameId");
        
        SortOperation sortByReviews = sort(
            Sort.by(Sort.Direction.DESC, "totalReviews")
        );
        
        LimitOperation limitResults = limit(limit);
        
        LookupOperation lookupGame = lookup(
            "games",
            "gameId",
            "_id",
            "gameDetails"
        );
        
        UnwindOperation unwindGame = unwind("gameDetails");
        
        ProjectionOperation projectResult = project()
            .and("gameId").as("gameId")
            .and("gameDetails.title").as("title")
            .and("totalReviews").as("totalReviews")
            .and("avgSentiment").as("avgSentiment")
            .and("avgRating").as("avgRating");
        
        Aggregation aggregation = newAggregation(
            groupByGame,
            sortByReviews,
            limitResults,
            lookupGame,
            unwindGame,
            projectResult
        );
        
        AggregationResults<SentimentAnalysis> results = mongoTemplate.aggregate(
            aggregation,
            "reviews",
            SentimentAnalysis.class
        );
        
        return results.getMappedResults();
    }
}