package com.gamesense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@EnableMongoRepositories(basePackages = "com.gamesense.repository.mongo")
@EnableNeo4jRepositories(basePackages = "com.gamesense.repository.neo4j")
@EnableCaching
@EnableRetry
@EnableAsync
@EnableScheduling
@Slf4j
public class GameSenseApplication {
    public static void main(String[] args) {
        SpringApplication.run(GameSenseApplication.class, args);
    }
    
    @PreDestroy
    public void onExit() {
        log.info("Application is shutting down. Closing resources...");
    }
}
