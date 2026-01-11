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
import java.util.Collections;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsAggregationService {

    private final MongoTemplate mongoTemplate;

    // Helper for ObjectId matching to bypass Variable import issues
    private AggregationOperation getCustomLookupOperation() {
        return context -> new Document("$lookup", new Document()
                .append("from", "games")
                .append("let", new Document("r_gameId", "$gameId"))
                .append("pipeline", Arrays.asList(
                        new Document("$match", new Document("$expr",
                                new Document("$eq", Arrays.asList(
                                        new Document("$toString", "$_id"),
                                        "$$r_gameId"
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
        
        MatchOperation matchRecent = match(new Criteria().orOperator(
            Criteria.where("timestamp").gte(cutoffDate),
            Criteria.where("createdAt").gte(cutoffDate)
        ));
        
        GroupOperation groupByGame = group("gameId")
            .count().as("reviewCount")
            .avg("rating").as("avgRating")
            .first("gameId").as("gameId");
        
        SortOperation sortByCount = sort(Sort.by(Sort.Direction.DESC, "reviewCount"));
        LimitOperation limitResults = limit(limit);
        
        AggregationOperation lookupGame = getCustomLookupOperation();
        UnwindOperation unwindGame = unwind("gameDetails");
        
        ProjectionOperation projectResult = project()
            .and("gameId").as("gameId")
            .and("gameDetails.title").as("title")
            .and("gameDetails.genres").as("genres")
            .and("gameDetails.coverImageUrl").as("coverImageUrl")
            .and("reviewCount").as("reviewCount")
            .and("avgRating").as("avgRating")
            .and(ArithmeticOperators.valueOf("reviewCount").multiplyBy("avgRating")).as("hypeScore");
        
        Aggregation aggregation = newAggregation(matchRecent, groupByGame, sortByCount, limitResults, lookupGame, unwindGame, projectResult);
        return mongoTemplate.aggregate(aggregation, "reviews", TrendingGame.class).getMappedResults();
    }

    /**
     * PIPELINE 2: Genre Dominance
     */
    public List<GenreTrend> analyzeGenreDominance() {
        log.info("Analyzing genre dominance");
        UnwindOperation unwindGenres = unwind("genres");
        
        ProjectionOperation extractYear = project()
            .and("genres").as("genre")
            .andExpression("year(releaseDate)").as("year");
        
        MatchOperation filterValidYears = match(Criteria.where("year").ne(null));
        
        GroupOperation groupByYearGenre = group("year", "genre")
            .count().as("gameCount")
            .first("year").as("year")
            .first("genre").as("genre");
        
        SortOperation sortByYearCount = sort(Sort.by(Sort.Direction.ASC, "year").and(Sort.by(Sort.Direction.DESC, "gameCount")));
        
        GroupOperation groupByYear = group("year")
            .first("year").as("year")
            .first("genre").as("dominantGenre")
            .first("gameCount").as("gameCount")
            .push(new BasicDBObject("genre", "$genre").append("count", "$gameCount")).as("genreBreakdown");
        
        SortOperation sortByYear = sort(Sort.by(Sort.Direction.ASC, "_id"));
        
        ProjectionOperation projectResult = project()
            .and("_id").as("year")
            .and("dominantGenre").as("dominantGenre")
            .and("gameCount").as("gameCount")
            .and("genreBreakdown").as("genreBreakdown");
        
        Aggregation aggregation = newAggregation(unwindGenres, extractYear, filterValidYears, groupByYearGenre, sortByYearCount, groupByYear, sortByYear, projectResult);
        return mongoTemplate.aggregate(aggregation, "games", GenreTrend.class).getMappedResults();
    }

    /**
     * PIPELINE 3: Team Win Rates
     */
    public List<TeamPerformance> calculateTeamWinRates(String gameTitle) {
        log.info("Calculating team win rates for game: {}", gameTitle);
        
        Criteria criteria = Criteria.where("status").is("FINISHED");
        if (gameTitle != null && !gameTitle.isEmpty()) {
            criteria = criteria.and("gameTitle").is(gameTitle);
        }
        MatchOperation matchFinished = match(criteria);
        
        // Safety Check
        long count = mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(criteria), "matches");
        if (count == 0) return Collections.emptyList();

        ProjectionOperation projectTeamA = project()
            .and("teamAId").as("teamId")
            .and("teamAName").as("teamName")
            .and("winnerId").as("winnerId")
            .and(ConditionalOperators.when(ComparisonOperators.valueOf("teamAId").equalTo("winnerId"))
                .then(1).otherwise(0)).as("isWin");
        
        ProjectionOperation projectTeamB = project()
            .and("teamBId").as("teamId")
            .and("teamBName").as("teamName")
            .and("winnerId").as("winnerId")
            .and(ConditionalOperators.when(ComparisonOperators.valueOf("teamBId").equalTo("winnerId"))
                .then(1).otherwise(0)).as("isWin");
        
        FacetOperation facetTeams = facet().and(projectTeamA).as("teamAMatches").and(projectTeamB).as("teamBMatches");
        
        ProjectionOperation mergeArrays = project().and(ArrayOperators.ConcatArrays.arrayOf("teamAMatches").concat("teamBMatches")).as("allMatches");
        
        UnwindOperation unwindMatches = unwind("allMatches");
        ReplaceRootOperation replaceRoot = replaceRoot("allMatches");
        
        GroupOperation groupByTeam = group("teamId")
            .first("teamName").as("teamName")
            .sum("isWin").as("wins")
            .count().as("totalMatches");
        
        // FIX: Nested ArithmeticOperators to resolve chaining issue
        ProjectionOperation calculateWinRate = project()
            .and("_id").as("teamId")
            .and("teamName").as("teamName")
            .and("wins").as("wins")
            .and("totalMatches").as("totalMatches")
            .and(ArithmeticOperators.valueOf(ArithmeticOperators.valueOf("wins").divideBy("totalMatches")).multiplyBy(100)).as("winRate");
        
        SortOperation sortByWinRate = sort(Sort.by(Sort.Direction.DESC, "winRate"));
        
        Aggregation aggregation = newAggregation(matchFinished, facetTeams, mergeArrays, unwindMatches, replaceRoot, groupByTeam, calculateWinRate, sortByWinRate);
        
        return mongoTemplate.aggregate(aggregation, "matches", TeamPerformance.class).getMappedResults();
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
        
        SortOperation sortByReviews = sort(Sort.by(Sort.Direction.DESC, "totalReviews"));
        LimitOperation limitResults = limit(limit);
        AggregationOperation lookupGame = getCustomLookupOperation();
        UnwindOperation unwindGame = unwind("gameDetails");
        
        ProjectionOperation projectResult = project()
            .and("gameId").as("gameId")
            .and("gameDetails.title").as("title")
            .and("totalReviews").as("totalReviews")
            .and("avgSentiment").as("avgSentiment")
            .and("avgRating").as("avgRating");
        
        Aggregation aggregation = newAggregation(groupByGame, sortByReviews, limitResults, lookupGame, unwindGame, projectResult);
        return mongoTemplate.aggregate(aggregation, "reviews", SentimentAnalysis.class).getMappedResults();
    }
}