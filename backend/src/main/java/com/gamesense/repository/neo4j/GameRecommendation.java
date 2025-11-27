package com.gamesense.repository.neo4j;

public interface GameRecommendation {
    String getGameId();
    String getTitle();
    Integer getCommonUsers();
}
