package com.gamesense.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "com.gamesense.repository.mongo")
@EnableNeo4jRepositories(basePackages = "com.gamesense.repository.neo4j")
public class DatabaseConfig {}