package com.nexus.campus.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JwtUtil}.
 *
 * <p>Covers token generation, validation, expiration, tamper detection,
 * and claim extraction.</p>
 */
class JwtUtilTest {

    private static final String TEST_SECRET = "OExHRjA3QjlGRjI0NUI0Q0E1RTdDRjI2MDA3QTUxMjhDOTdBNkY4RjI0NUI0Q0E1RTdDRjI2MDdCOTg2RjA3Qg==";
    private static final long TEST_EXPIRATION = 86400000L; // 24 hours

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", TEST_EXPIRATION);
        jwtUtil.init();
    }

    @Test
    @DisplayName("Generate a valid token and verify it passes validation")
    void generateAndValidateToken() {
        String token = jwtUtil.generateToken(1L, "testuser", "USER");

        assertNotNull(token);
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    @DisplayName("Extract userId, username, and role from a valid token")
    void extractClaims() {
        String token = jwtUtil.generateToken(42L, "netrunner", "ADMIN");

        assertEquals(42L, jwtUtil.getUserIdFromToken(token));
        assertEquals("netrunner", jwtUtil.getUsernameFromToken(token));
        assertEquals("ADMIN", jwtUtil.getRoleFromToken(token));
    }

    @Test
    @DisplayName("Expired token should fail validation")
    void expiredTokenShouldFail() {
        // Build an already-expired token using the same key
        byte[] keyBytes = Base64.getDecoder().decode(TEST_SECRET);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        String expiredToken = Jwts.builder()
                .subject("1")
                .claim("username", "expired")
                .claim("role", "USER")
                .issuedAt(new Date(System.currentTimeMillis() - 100000))
                .expiration(new Date(System.currentTimeMillis() - 50000))
                .signWith(key)
                .compact();

        assertFalse(jwtUtil.validateToken(expiredToken));
    }

    @Test
    @DisplayName("Tampered token should fail validation")
    void tamperedTokenShouldFail() {
        String token = jwtUtil.generateToken(1L, "testuser", "USER");
        // Corrupt the payload section
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "tampered." + parts[2];

        assertFalse(jwtUtil.validateToken(tampered));
    }

    @Test
    @DisplayName("Null and malformed tokens should fail validation")
    void invalidTokensShouldFail() {
        assertFalse(jwtUtil.validateToken(null));
        assertFalse(jwtUtil.validateToken(""));
        assertFalse(jwtUtil.validateToken("not.a.token"));
    }

    @Test
    @DisplayName("Tokens for different users carry distinct claims")
    void distinctUsers() {
        String tokenA = jwtUtil.generateToken(1L, "alice", "USER");
        String tokenB = jwtUtil.generateToken(2L, "bob", "USER");

        assertNotEquals(tokenA, tokenB);
        assertEquals(1L, jwtUtil.getUserIdFromToken(tokenA));
        assertEquals(2L, jwtUtil.getUserIdFromToken(tokenB));
    }
}
