package com.canteen.service;

import com.canteen.dto.AuthRequest;
import com.canteen.dto.AuthResponse;
import com.canteen.model.User;
import com.canteen.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecretKey signingKey;

    public AuthService(UserRepository userRepository, @Value("${app.auth-secret}") String authSecret) {
        this.userRepository = userRepository;
        byte[] keyBytes = authSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                keyBytes = md.digest(keyBytes);
            } catch (Exception ignored) {}
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("name", user.getName())
                .claim("email", user.getEmail())
                .expiration(new Date(System.currentTimeMillis() + 7L * 24 * 3600 * 1000))
                .signWith(signingKey)
                .compact();
    }

    public Long parseTokenUserId(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            String clean = token.replace("Bearer ", "").trim();
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(clean)
                    .getPayload();
            return Long.parseLong(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    public User getUserByToken(String authHeader) {
        Long userId = parseTokenUserId(authHeader);
        if (userId == null) return null;
        return userRepository.findById(userId).orElse(null);
    }

    public AuthResponse register(AuthRequest req) {
        String name = req.getName() != null ? req.getName().trim() : "";
        String email = req.getEmail() != null ? req.getEmail().trim().toLowerCase() : "";
        String password = req.getPassword() != null ? req.getPassword() : "";

        if (name.isEmpty() || !email.matches("^\\S+@\\S+\\.\\S+$") || password.length() < 6) {
            throw new IllegalArgumentException("Enter name, a valid email and a password of at least 6 characters");
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("This email is already registered");
        }

        User user = new User(name, email, passwordEncoder.encode(password));
        user = userRepository.save(user);

        return new AuthResponse(generateToken(user), user);
    }

    public AuthResponse login(AuthRequest req) {
        String email = req.getEmail() != null ? req.getEmail().trim().toLowerCase() : "";
        String password = req.getPassword() != null ? req.getPassword() : "";

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new SecurityException("Wrong email or password");
        }

        return new AuthResponse(generateToken(user), user);
    }
}
