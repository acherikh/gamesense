package com.gamesense.service;

import com.gamesense.dto.analytics.*;
import com.mongodb.BasicDBObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document; 
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsAggregationService {

    private final MongoTemplate mongoTemplate;

    /**
     * Helper method to create a custom $lookup stage
     * This bypasses the "Variable cannot be resolved" error by building the raw BSON directly.
     * It also handles the ObjectId -> String conversion for joining.
     */
    private AggregationOperation getCustomLookupOperation() {
        return context -> new Document("$lookup", new Document()
                .append("from", "games")
                .append("let", new Document("r_gameId", "$gameId"))
                .append("pipeline", Arrays.asList(
                        new Document("$match", new Document("$expr",
                                new Document("$eq", Arrays.asList(
                                        new Document("$toString", "$_id"), // Convert Game ObjectId to String
                                        "$$r_gameId"                       // Match against Review gameId
                                ))
                        ))
                ))
                .append("as", "gameDetails"));
    }

    /**
     * PIPELINE 1: Hype Meter
     */
    public List<TrendingGame> calculateHypeMeter(int days, int limit) {
        log.info("Calculating Hype Meter for last {} days", days);
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        
        // Match stage: Filter recent reviews
        MatchOperation matchRecent = match(
            new Criteria().orOperator(
                Criteria.where("timestamp").gte(cutoffDate),
                Criteria.where("createdAt").gte(cutoffDate)
            )
        );
        
        // Group stage: Calculate stats per game
        GroupOperation groupByGame = group("gameId")
            .count().as("reviewCount")
            .avg("rating").as("avgRating")
            .first("gameId").as("gameId");
        
        // Sort stage: Order by review count
        SortOperation sortByCount = sort(
            Sort.by(Sort.Direction.DESC, "reviewCount")
        );
        
        // Limit stage
        LimitOperation limitResults = limit(limit);
        
        // Custom Lookup (Replaces the broken Variable/MongoExpression code)
        AggregationOperation lookupGame = getCustomLookupOperation();
        
        // Unwind stage
        UnwindOperation unwindGame = unwind("gameDetails");
        
        // Project stage
        ProjectionOperation projectResult = project()
            .and("gameId").as("gameId")
            .and("gameDetails.title").as("title")
            .and("gameDetails.genres").as("genres")
            .and("gameDetails.coverImageUrl").as("coverImageUrl")
            .and("reviewCount").as("reviewCount")
            .and("avgRating").as("avgRating")
            .andExpression("reviewCount * avgRating").as("hypeScore");
        
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
        
        return results.getMappedResults();
    }

    /**
     * PIPELINE 2: Genre Dominance Over Time
     */
    public List<GenreTrend> analyzeGenreDominance() {
        log.info("Analyzing genre dominance over time");
        
        UnwindOperation unwindGenres = unwind("genres");
        
        ProjectionOperation extractYear = project()
            .and("genres").as("genre")
            .andExpression("year(releaseDate)").as("year");
        
        MatchOperation filterValidYears = match(
            Criteria.where("year").ne(null)
        );
        
        GroupOperation groupByYearGenre = group("year", "genre")
            .count().as("gameCount")
            .first("year").as("year")
            .first("genre").as("genre");
        
        SortOperation sortByYearCount = sort(
            Sort.by(Sort.Direction.ASC, "year")
                .and(Sort.by(Sort.Direction.DESC, "gameCount"))
        );
        
        GroupOperation groupByYear = group("year")
            .first("year").as("year")
            .first("genre").as("dominantGenre")
            .first("gameCount").as("gameCount")
            .push(new BasicDBObject("genre", "$genre")
                .append("count", "$gameCount")).as("genreBreakdown");
        
        SortOperation sortByYear = sort(
            Sort.by(Sort.Direction.ASC, "_id")
        );
        
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
     * PIPELINE 3: Esports Team Win Rates
     */
    public List<TeamPerformance> calculateTeamWinRates(String gameTitle) {
        log.info("Calculating team win rates for game: {}", gameTitle);
        
        Criteria criteria = Criteria.where("status").is("FINISHED");
        if (gameTitle != null && !gameTitle.isEmpty()) {
            criteria = criteria.and("gameTitle").is(gameTitle);
        }
        MatchOperation matchFinished = match(criteria);
        
        // Correct logic: Compare field values (not strings)
        ProjectionOperation projectTeamA = project()
            .and("teamAId").as("teamId")
            .and("teamAName").as("teamName")
            .and("winnerId").as("winnerId")
            .andExpression("teamAId == winnerId ? 1 : 0").as("isWin");
        
        ProjectionOperation projectTeamB = project()
            .and("teamBId").as("teamId")
            .and("teamBName").as("teamName")
            .and("winnerId").as("winnerId")
            .andExpression("teamBId == winnerId ? 1 : 0").as("isWin");
        
        FacetOperation facetTeams = facet()
            .and(projectTeamA).as("teamAMatches")
            .and(projectTeamB).as("teamBMatches");
        
        ProjectionOperation mergeArrays = project()
            .and(ArrayOperators.ConcatArrays.arrayOf("teamAMatches")
                .concat("teamBMatches"))
            .as("allMatches");
        
        UnwindOperation unwindMatches = unwind("allMatches");
        
        ReplaceRootOperation replaceRoot = replaceRoot("allMatches");
        
        GroupOperation groupByTeam = group("teamId")
            .first("teamName").as("teamName")
            .sum("isWin").as("wins")
            .count().as("totalMatches");
        
        ProjectionOperation calculateWinRate = project()
            .and("_id").as("teamId")
            .and("teamName").as("teamName")
            .and("wins").as("wins")
            .and("totalMatches").as("totalMatches")
            .andExpression("(wins / totalMatches) * 100").as("winRate");
        
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
     * Sentiment Analysis
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
        
        // Use the custom lookup here as well
        AggregationOperation lookupGame = getCustomLookupOperation();
        
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