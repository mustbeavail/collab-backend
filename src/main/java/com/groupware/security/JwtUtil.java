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

    @Value("${jwt.secret}")
    public void setSecret(String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Value("${jwt.access-token-expiry}")
    public void setAccessTokenExpiry(long expiry) {
        this.accessTokenExpiry = expiry;
    }

    public String generateAccessToken(String userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiry))
                .signWith(key)
                .compact();
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
