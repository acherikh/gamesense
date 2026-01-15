package com.gamesense.config;

import com.gamesense.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
            // 1. PUBLIC ENDPOINTS (Guest Access)
            .requestMatchers("/api/auth/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
            .requestMatchers("/actuator/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/games/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/matches/**").permitAll()
            
            // ANALYTICS (Public)
            .requestMatchers(HttpMethod.GET, "/api/analytics/trending").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/analytics/genres/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/analytics/teams/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/analytics/sentiment").permitAll()

            // 2. ADMIN ENDPOINTS (Strict Access)
            // Games
            .requestMatchers(HttpMethod.POST, "/api/games/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/games/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/games/**").hasRole("ADMIN")
            // FIX: Matches (Locking out Eve)
            .requestMatchers(HttpMethod.POST, "/api/matches/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/matches/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/matches/**").hasRole("ADMIN")
            // Analytics Admin
            .requestMatchers("/api/analytics/admin/**").hasRole("ADMIN")
            
            // 3. REGISTERED USER ENDPOINTS
            .requestMatchers("/api/reviews/**").authenticated()
            .requestMatchers("/api/users/**").authenticated()
            .requestMatchers("/api/graph/**").authenticated() 
            
            // 4. Default deny
            .anyRequest().authenticated()
        )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true); 
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}