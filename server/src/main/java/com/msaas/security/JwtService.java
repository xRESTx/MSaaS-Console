package com.msaas.security;

import com.msaas.config.AppProperties;
import com.msaas.user.SystemRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final AppProperties properties;

    public JwtService(AppProperties properties) {
        this.properties = properties;
    }

    public String createToken(String userId, String email, String username, SystemRole systemRole) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getJwt().getTtlMinutes() * 60);
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("username", username)
                .claim("systemRole", (systemRole == null ? SystemRole.USER : systemRole).name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key())
                .compact();
    }

    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String role = claims.get("systemRole", String.class);
        return new AuthenticatedUser(claims.getSubject(), claims.get("email", String.class), claims.get("username", String.class), parseRole(role));
    }

    private SystemRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            return SystemRole.USER;
        }
        try {
            return SystemRole.valueOf(role);
        } catch (IllegalArgumentException ignored) {
            return SystemRole.USER;
        }
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
