package com.groupware.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-hmac-sha!!";
    private static final long EXPIRY = 3_600_000L;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, EXPIRY);
    }

    @Test
    void 토큰_생성_후_userId_파싱() {
        String userId = "user-abc-123";
        String token = jwtUtil.generateAccessToken(userId);
        assertThat(jwtUtil.getUserId(token)).isEqualTo(userId);
    }

    @Test
    void 유효한_토큰_검증_성공() {
        String token = jwtUtil.generateAccessToken("user-123");
        assertThat(jwtUtil.validate(token)).isTrue();
    }

    @Test
    void 변조된_토큰_검증_실패() {
        String token = jwtUtil.generateAccessToken("user-123") + "tampered";
        assertThat(jwtUtil.validate(token)).isFalse();
    }

    @Test
    void 빈_문자열_검증_실패() {
        assertThat(jwtUtil.validate("")).isFalse();
    }

    @Test
    void 만료된_토큰_검증_실패() {
        JwtUtil expiredJwtUtil = new JwtUtil(SECRET, -1L);
        String token = expiredJwtUtil.generateAccessToken("user-123");
        assertThat(jwtUtil.validate(token)).isFalse();
    }
}
