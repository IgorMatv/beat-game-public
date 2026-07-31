package com.beatgame.auth;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long-for-hs256";

    private final JwtService jwtService = new JwtService(SECRET, 6);

    @Test
    void issue_thenVerify_roundTripsClaims() {
        String jwt = jwtService.issue("player-uuid-123", "ABC123");

        AuthClaims claims = jwtService.verify(jwt);

        assertThat(claims.playerToken()).isEqualTo("player-uuid-123");
        assertThat(claims.roomCode()).isEqualTo("ABC123");
    }

    @Test
    void verify_rejectsTamperedSignature() {
        String jwt = jwtService.issue("player-uuid-123", "ABC123");
        String tampered = jwt.substring(0, jwt.length() - 4) + "abcd";

        assertThatThrownBy(() -> jwtService.verify(tampered))
            .isInstanceOf(SignatureException.class);
    }

    @Test
    void verify_rejectsExpiredToken() {
        JwtService expiredImmediately = new JwtService(SECRET, 0);
        String jwt = expiredImmediately.issue("player-uuid-123", "ABC123");

        assertThatThrownBy(() -> expiredImmediately.verify(jwt))
            .isInstanceOf(ExpiredJwtException.class);
    }
}
