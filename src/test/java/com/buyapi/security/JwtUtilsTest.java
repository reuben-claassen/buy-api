package com.buyapi.security;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    private static final String TEST_SECRET =
            "dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLWhzMjU2";
    private static final long EXPIRATION_MS = 3_600_000L;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", EXPIRATION_MS);
    }

    private UserDetails userDetails(String email) {
        return User.withUsername(email).password("irrelevant").authorities(List.of()).build();
    }

    @Test
    void generateToken_returnsNonNullToken() {
        String token = jwtUtils.generateToken(userDetails("user@example.com"));
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void generateToken_tokenHasThreeParts() {
        String token = jwtUtils.generateToken(userDetails("user@example.com"));
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void extractUsername_returnsCorrectEmail() {
        String token = jwtUtils.generateToken(userDetails("user@example.com"));
        assertThat(jwtUtils.extractUsername(token)).isEqualTo("user@example.com");
    }

    @Test
    void extractUsername_differentUsers_returnCorrectEmails() {
        String token1 = jwtUtils.generateToken(userDetails("alice@example.com"));
        String token2 = jwtUtils.generateToken(userDetails("bob@example.com"));
        assertThat(jwtUtils.extractUsername(token1)).isEqualTo("alice@example.com");
        assertThat(jwtUtils.extractUsername(token2)).isEqualTo("bob@example.com");
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        String token = jwtUtils.generateToken(userDetails("user@example.com"));
        assertThat(jwtUtils.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_tamperedSignature_returnsFalse() {
        String token = jwtUtils.generateToken(userDetails("user@example.com"));
        int lastDot = token.lastIndexOf('.');
        String signature = token.substring(lastDot + 1);
        char original = signature.charAt(0);
        char replacement = (original == 'A') ? 'B' : 'A';
        String tampered = token.substring(0, lastDot + 1) + replacement + signature.substring(1);
        assertThat(jwtUtils.validateToken(tampered)).isFalse();
    }

    @Test
    void validateToken_randomGarbage_returnsFalse() {
        assertThat(jwtUtils.validateToken("not.a.jwt")).isFalse();
    }

    @Test
    void validateToken_emptyString_returnsFalse() {
        assertThat(jwtUtils.validateToken("")).isFalse();
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", -1000L);
        String token = jwtUtils.generateToken(userDetails("user@example.com"));
        assertThat(jwtUtils.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_wrongSecret_returnsFalse() {
        String token = jwtUtils.generateToken(userDetails("user@example.com"));

        JwtUtils other = new JwtUtils();
        ReflectionTestUtils.setField(other, "jwtSecret",
                "b3RoZXItc2VjcmV0LWtleS10aGF0LWlzLWxvbmctZW5vdWdoLWZvci1oczI1Ng==");
        ReflectionTestUtils.setField(other, "jwtExpirationMs", EXPIRATION_MS);

        assertThat(other.validateToken(token)).isFalse();
    }
}