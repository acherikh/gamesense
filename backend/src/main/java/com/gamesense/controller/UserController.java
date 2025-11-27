package com.gamesense.controller;

import com.gamesense.model.mongo.*;
import com.gamesense.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.gamesense.model.neo4j.GameStatus;

import jakarta.validation.Valid;

// ========== User Controller ==========
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management")
public class UserController {

    private final UserService userService;
    private final ConsistencyService consistencyService;

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        return userService.getUserById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create user")
    public ResponseEntity<User> createUser(@Valid @RequestBody User user) {
        return ResponseEntity.ok(userService.createUser(user));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user")
    public ResponseEntity<User> updateUser(
            @PathVariable String id,
            @Valid @RequestBody User user) {
        return ResponseEntity.ok(userService.updateUser(id, user));
    }

    @PostMapping("/{userId}/games/{gameId}")
    @Operation(summary = "Add game to user library")
    public ResponseEntity<Void> addGameToLibrary(
            @PathVariable String userId,
            @PathVariable String gameId,
            @RequestParam String status) {
        consistencyService.addGameToLibrary(
            userId,
            gameId,
            GameStatus.valueOf(status.toUpperCase())
        );
        return ResponseEntity.ok().build();
    }
}
