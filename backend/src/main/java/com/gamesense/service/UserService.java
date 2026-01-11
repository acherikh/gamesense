package com.gamesense.service;

import com.gamesense.model.mongo.User;
import com.gamesense.model.neo4j.UserNode;
import com.gamesense.repository.mongo.UserRepository;
import com.gamesense.repository.neo4j.UserNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserNodeRepository userNodeRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        
        Set<SimpleGrantedAuthority> authorities = user.getRoles() != null 
                ? user.getRoles().stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet())
                : Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                authorities
        );
    }

    public User registerUser(User user) {
        log.info("Registering new user: {}", user.getUsername());
        
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
        
        // Secure Password
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        
        // Default Role
        user.setRoles(new HashSet<>(Collections.singletonList("ROLE_USER")));
        
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        
        User savedUser = userRepository.save(user);
        
        // Sync to Neo4j (Best Effort)
        try {
            UserNode userNode = new UserNode();
            userNode.setUserId(savedUser.getId());
            userNode.setUsername(savedUser.getUsername());
            userNode.setCreatedAt(savedUser.getCreatedAt());
            userNodeRepository.save(userNode);
        } catch (Exception e) {
            log.error("Failed to sync user to Neo4j: {}", e.getMessage());
        }
        
        return savedUser;
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User getUserById(String id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
    }
}