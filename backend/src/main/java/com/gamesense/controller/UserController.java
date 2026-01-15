package com.gamesense.controller;

import com.gamesense.model.mongo.User;
import com.gamesense.model.neo4j.GameStatus; // Import GameStatus
import com.gamesense.security.JwtTokenProvider;
import com.gamesense.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api") // Changed to /api to match test script paths like /api/auth and /api/users
@RequiredArgsConstructor
@Tag(name = "User", description = "User management and auth")
public class UserController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    // --- AUTH ENDPOINTS ---

    @PostMapping("/auth/register")
    @Operation(summary = "Register new user")
    public ResponseEntity<User> register(@RequestBody User user) {
        return ResponseEntity.ok(userService.registerUser(user));
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Authenticate user and get token")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        // 1. Authenticate
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        // 2. Generate Token
        String token = jwtTokenProvider.generateToken(authentication);
        
        // 3. FIX: Fetch User details to return ID
        User user = userService.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User found in auth but not in DB"));

        // 4. Return rich response
        return ResponseEntity.ok(new AuthResponse(token, user.getId(), user.getUsername(), user.getEmail()));
    }

    // --- USER ENDPOINTS ---

    @PostMapping("/users/{userId}/games/{gameId}")
    @Operation(summary = "Add game to user library (Dual Write)")
    public ResponseEntity<Void> addGameToLibrary(
            @PathVariable String userId,
            @PathVariable String gameId,
            @RequestParam(defaultValue = "PLAYING") GameStatus status) {
        
        userService.addGameToLibrary(userId, gameId, status);
        return ResponseEntity.ok().build();
    }
    
    @Data
    public static class AuthRequest {
        private String username;
        private String password;
    }
    
    @Data
    public static class AuthResponse {
        private String token;
        private String id;
        private String username;
        private String email;

        public AuthResponse(String token, String id, String username, String email) { 
            this.token = token; 
            this.id = id;
            this.username = username;
            this.email = email;
        }
    }
}