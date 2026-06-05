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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    private static final String REFRESH_KEY_PREFIX = "refresh:";

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsById(request.getEmail())) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = new User();
        user.setUserId(request.getEmail());
        user.setPw(passwordEncoder.encode(request.getPassword()));
        user.setNick(request.getNickname());
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
        redisTemplate.delete(REFRESH_KEY_PREFIX + refreshToken);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getUserId());
        String refreshToken = UUID.randomUUID().toString();

        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + refreshToken,
                user.getUserId(),
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
