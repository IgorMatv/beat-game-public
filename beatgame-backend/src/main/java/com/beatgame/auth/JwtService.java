package com.beatgame.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private static final String ROOM_CODE_CLAIM = "roomCode";

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(@Value("${jwt.secret}") String secret, @Value("${jwt.ttl-hours}") long ttlHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofHours(ttlHours);
    }

    public String issue(String playerToken, String roomCode) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(playerToken)
            .claim(ROOM_CODE_CLAIM, roomCode)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(key)
            .compact();
    }

    public AuthClaims verify(String jwt) {
        var claims = Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(jwt)
            .getPayload();
        return new AuthClaims(claims.getSubject(), claims.get(ROOM_CODE_CLAIM, String.class));
    }
}
