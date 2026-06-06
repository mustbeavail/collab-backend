package com.groupware.service;

import com.groupware.domain.User;
import com.groupware.dto.auth.AuthResponse;
import com.groupware.dto.auth.LoginRequest;
import com.groupware.dto.auth.SignupRequest;
import com.groupware.dto.auth.TokenRefreshRequest;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.UserRepository;
import com.groupware.security.JwtUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String USER_SESSION_KEY_PREFIX = "user:";

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        Optional<User> existingOpt = userRepository.findById(request.getEmail());
        if (existingOpt.isPresent()) {
            User existing = existingOpt.get();
            if (existing.getWithdrwalAt() == null) {
                throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
            // 탈퇴 회원 재가입: 기존 행의 user_id를 _1, _2, ... 로 변경
            renameWithdrawnUser(request.getEmail());
            entityManager.clear();
        }

        User user = new User();
        user.setUserId(request.getEmail());
        user.setPw(passwordEncoder.encode(request.getPassword()));
        user.setNick(request.getNickname());
        user.setAbout(request.getAbout());
        user.setJoinAt(LocalDateTime.now());
        userRepository.save(user);

        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findById(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.EMAIL_NOT_REGISTERED));

        if (user.getWithdrwalAt() != null) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPw())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokens(user);
    }

    public AuthResponse refresh(TokenRefreshRequest request) {
        String key = REFRESH_KEY_PREFIX + request.getRefreshToken();
        String userId = redisTemplate.opsForValue().get(key);

        if (userId == null) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        redisTemplate.delete(key);
        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        String userId = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + refreshToken);
        redisTemplate.delete(REFRESH_KEY_PREFIX + refreshToken);
        if (userId != null) {
            String userSessionKey = USER_SESSION_KEY_PREFIX + userId;
            String storedToken = redisTemplate.opsForValue().get(userSessionKey);
            if (refreshToken.equals(storedToken)) {
                redisTemplate.delete(userSessionKey);
            }
        }
    }

    public void checkEmail(String email) {
        if (userRepository.existsByUserIdAndWithdrwalAtIsNull(email)) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    public void checkNickname(String nickname) {
        if (userRepository.existsByNickAndWithdrwalAtIsNull(nickname)) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
    }

    private void renameWithdrawnUser(String email) {
        String newEmail = findNextRenamedEmail(email);
        try {
            entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS=0").executeUpdate();
            entityManager.createNativeQuery("UPDATE users SET user_id = :newId WHERE user_id = :oldId")
                    .setParameter("newId", newEmail)
                    .setParameter("oldId", email)
                    .executeUpdate();
        } finally {
            entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS=1").executeUpdate();
        }
    }

    private String findNextRenamedEmail(String email) {
        int suffix = 1;
        while (userRepository.existsById(email + "_" + suffix)) {
            suffix++;
        }
        return email + "_" + suffix;
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getUserId());
        String refreshToken = UUID.randomUUID().toString();

        // 기존 세션 무효화 — 역방향 매핑에서 이전 refreshToken 조회 후 삭제
        String userSessionKey = USER_SESSION_KEY_PREFIX + user.getUserId();
        String oldRefreshToken = redisTemplate.opsForValue().get(userSessionKey);
        if (oldRefreshToken != null) {
            redisTemplate.delete(REFRESH_KEY_PREFIX + oldRefreshToken);
            messagingTemplate.convertAndSendToUser(
                    user.getUserId(),
                    "/queue/session",
                    Map.of("type", "FORCE_LOGOUT")
            );
        }

        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + refreshToken,
                user.getUserId(),
                refreshTokenExpiry,
                TimeUnit.MILLISECONDS
        );
        redisTemplate.opsForValue().set(
                userSessionKey,
                refreshToken,
                refreshTokenExpiry,
                TimeUnit.MILLISECONDS
        );

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getUserId())
                .nickname(user.getNick())
                .email(user.getUserId())
                .build();
    }
}
