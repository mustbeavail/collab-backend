package com.groupware.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final StringRedisTemplate redisTemplate;

    private SecretKey key;
    private long accessTokenExpiry;

    private static final String BLACKLIST_PREFIX = "blacklist:";
    /** 사용자별 현재 활성 세션 id 저장 키. AuthService가 로그인/갱신 시 기록한다. */
    public static final String SESSION_KEY_PREFIX = "session:";
    private static final String SID_CLAIM = "sid";

    @Value("${jwt.secret}")
    public void setSecret(String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Value("${jwt.access-token-expiry}")
    public void setAccessTokenExpiry(long expiry) {
        this.accessTokenExpiry = expiry;
    }

    public String generateAccessToken(String userId) {
        return generateAccessToken(userId, null);
    }

    public String generateAccessToken(String userId, String sessionId) {
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(userId)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiry));
        if (sessionId != null) {
            builder.claim(SID_CLAIM, sessionId);
        }
        return builder.signWith(key).compact();
    }

    public String getSessionId(String token) {
        return getClaims(token).get(SID_CLAIM, String.class);
    }

    /**
     * 토큰의 sid 클레임이 redis에 저장된 사용자의 현재 활성 세션과 일치하는지 검사.
     * 새 로그인이 일어나면 redis 세션 id가 갱신되어 기존 토큰은 즉시 거부된다(새로고침 불필요).
     * sid가 없는 구버전 토큰은 거부하여 재로그인을 강제한다.
     */
    public boolean isCurrentSession(String token) {
        try {
            String sid = getSessionId(token);
            if (sid == null) {
                return false;
            }
            String current = redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + getUserId(token));
            return sid.equals(current);
        } catch (Exception e) {
            return false;
        }
    }

    public String getUserId(String token) {
        return getClaims(token).getSubject();
    }

    public String getJti(String token) {
        return getClaims(token).getId();
    }

    public long getExpiryMillis(String token) {
        return getClaims(token).getExpiration().getTime();
    }

    public void blacklist(String token) {
        try {
            String jti = getJti(token);
            long ttl = getExpiryMillis(token) - System.currentTimeMillis();
            if (ttl > 0) {
                redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "1", ttl, TimeUnit.MILLISECONDS);
            }
        } catch (Exception ignored) {
            // 이미 만료된 토큰은 블랙리스트 불필요
        }
    }

    public boolean isBlacklisted(String token) {
        try {
            String jti = getJti(token);
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validate(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
