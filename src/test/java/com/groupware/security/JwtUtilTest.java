package com.groupware.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

class JwtUtilTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-hmac-sha!!";
    private static final long EXPIRY = 3_600_000L;

    private JwtUtil jwtUtil;
    private StringRedisTemplate mockRedis;

    @BeforeEach
    void setUp() {
        mockRedis = Mockito.mock(StringRedisTemplate.class);
        jwtUtil = new JwtUtil(mockRedis);
        jwtUtil.setSecret(SECRET);
        jwtUtil.setAccessTokenExpiry(EXPIRY);
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
        StringRedisTemplate expiredRedis = Mockito.mock(StringRedisTemplate.class);
        JwtUtil expiredJwtUtil = new JwtUtil(expiredRedis);
        expiredJwtUtil.setSecret(SECRET);
        expiredJwtUtil.setAccessTokenExpiry(-1L);
        String token = expiredJwtUtil.generateAccessToken("user-123");
        assertThat(jwtUtil.validate(token)).isFalse();
    }

    // ─── 세션 id(sid) ──────────────────────────────────────────────────────

    @Test
    void sid_클레임_파싱() {
        String token = jwtUtil.generateAccessToken("user-123", "sess-1");
        assertThat(jwtUtil.getSessionId(token)).isEqualTo("sess-1");
    }

    @Test
    void sid_없는_토큰_getSessionId_null() {
        String token = jwtUtil.generateAccessToken("user-123");
        assertThat(jwtUtil.getSessionId(token)).isNull();
    }

    @Test
    void 현재_세션이면_isCurrentSession_true() {
        ValueOperations<String, String> valueOps = mockValueOps();
        given(valueOps.get("session:user-123")).willReturn("sess-1");
        String token = jwtUtil.generateAccessToken("user-123", "sess-1");
        assertThat(jwtUtil.isCurrentSession(token)).isTrue();
    }

    @Test
    void 새_로그인으로_sid_갱신되면_기존_토큰_isCurrentSession_false() {
        ValueOperations<String, String> valueOps = mockValueOps();
        given(valueOps.get("session:user-123")).willReturn("sess-2"); // 새 세션으로 갱신됨
        String oldToken = jwtUtil.generateAccessToken("user-123", "sess-1");
        assertThat(jwtUtil.isCurrentSession(oldToken)).isFalse();
    }

    @Test
    void sid_없는_구버전_토큰은_isCurrentSession_false() {
        String token = jwtUtil.generateAccessToken("user-123");
        assertThat(jwtUtil.isCurrentSession(token)).isFalse();
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> mockValueOps() {
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        given(mockRedis.opsForValue()).willReturn(valueOps);
        return valueOps;
    }
}
