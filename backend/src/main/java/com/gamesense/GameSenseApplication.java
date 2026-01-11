package com.gamesense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class GameSenseApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameSenseApplication.class, args);
    }
}