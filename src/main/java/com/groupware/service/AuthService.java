package com.groupware.service;

import com.groupware.domain.User;
import com.groupware.dto.auth.AuthResponse;
import com.groupware.dto.auth.LoginRequest;
import com.groupware.dto.auth.SignupRequest;
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
    private final EmailVerificationService emailVerificationService;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String USER_SESSION_KEY_PREFIX = "user:";

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        // 1) 인증 여부만 먼저 검증(키 삭제는 저장 성공 후) — 저장 실패 시 재인증 강요 방지
        emailVerificationService.requireVerified(request.getEmail());

        Optional<User> existingOpt = userRepository.findById(request.getEmail());
        if (existingOpt.isPresent()) {
            User existing = existingOpt.get();
            if (existing.getWithdrawalAt() == null) {
                throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
            // 탈퇴 회원 재가입: 기존 행의 user_id를 _1, _2, ... 로 변경
            renameWithdrawnUser(request.getEmail());
            entityManager.clear();
        }

        // 2) 닉네임 중복 검증(활성 유저 기준). 탈퇴 유저는 nick=NULL이라 자동 제외됨.
        if (userRepository.existsByNickAndWithdrawalAtIsNull(request.getNickname())) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        User user = new User();
        user.setUserId(request.getEmail());
        user.setPw(passwordEncoder.encode(request.getPassword()));
        user.setNick(request.getNickname());
        user.setAbout(request.getAbout());
        user.setJoinAt(LocalDateTime.now());
        userRepository.save(user);

        // 3) 저장 성공 후에야 인증 키 소비(삭제)
        emailVerificationService.consumeVerified(request.getEmail());

        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findById(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.EMAIL_NOT_REGISTERED));

        if (user.getWithdrawalAt() != null) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPw())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        String key = REFRESH_KEY_PREFIX + refreshToken;
        String userId = redisTemplate.opsForValue().get(key);

        if (userId == null) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        redisTemplate.delete(key);
        return issueTokens(user);
    }

    public void logout(String refreshToken, String accessToken) {
        if (refreshToken != null) {
            String userId = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + refreshToken);
            redisTemplate.delete(REFRESH_KEY_PREFIX + refreshToken);
            if (userId != null) {
                String userSessionKey = USER_SESSION_KEY_PREFIX + userId;
                String storedToken = redisTemplate.opsForValue().get(userSessionKey);
                if (refreshToken.equals(storedToken)) {
                    redisTemplate.delete(userSessionKey);
                    // 현재 세션 id도 제거 → 남은 access token도 즉시 거부됨
                    redisTemplate.delete(JwtUtil.SESSION_KEY_PREFIX + userId);
                }
            }
        }
        if (accessToken != null) {
            jwtUtil.blacklist(accessToken);
        }
    }

    public void checkEmail(String email) {
        if (userRepository.existsByUserIdAndWithdrawalAtIsNull(email)) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    public void checkNickname(String nickname, String excludeUserId) {
        boolean taken = (excludeUserId != null)
                ? userRepository.existsNickByOtherUser(nickname, excludeUserId)
                : userRepository.existsByNickAndWithdrawalAtIsNull(nickname);
        if (taken) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
    }

    private void renameWithdrawnUser(String email) {
        String newEmail = findNextRenamedEmail(email);
        // 자식 FK에 ON UPDATE CASCADE 설정됨(db/migration_20260609_fk_cascade_nick_unique.sql)
        // → users.user_id rename 시 자식 테이블(user_id/friend_id/guest_id) 자동 반영
        entityManager.createNativeQuery("UPDATE users SET user_id = :newId WHERE user_id = :oldId")
                .setParameter("newId", newEmail)
                .setParameter("oldId", email)
                .executeUpdate();
    }

    private static final int USER_ID_MAX_LENGTH = 254; // User.userId 컬럼 길이

    private String findNextRenamedEmail(String email) {
        int suffix = 1;
        String candidate;
        do {
            candidate = buildRenamedId(email, suffix);
            suffix++;
        } while (userRepository.existsById(candidate));
        return candidate;
    }

    /** email + "_N" 형태로 만들되, 컬럼 길이(254)를 넘으면 email 앞부분을 잘라 맞춘다. */
    private String buildRenamedId(String email, int suffix) {
        String tag = "_" + suffix;
        int maxBase = USER_ID_MAX_LENGTH - tag.length();
        String base = email.length() > maxBase ? email.substring(0, maxBase) : email;
        return base + tag;
    }

    /**
     * 시연(데모) 전용: 비밀번호 검증 없이 토큰을 발급한다.
     * 반드시 서버가 통제하는 데모 계정(DemoAccountService의 허용목록)에만 호출해야 한다.
     */
    public AuthResponse issueDemoTokens(User user) {
        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        // sessionId: access token에 sid 클레임으로 박고 redis에 현재 세션으로 기록 →
        // 새 로그인 시 sid 갱신되어 기존 access token이 서버에서 즉시 거부됨(새로고침 불필요)
        String sessionId = UUID.randomUUID().toString();
        String accessToken = jwtUtil.generateAccessToken(user.getUserId(), sessionId);
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
        // 현재 활성 세션 id 기록(JwtUtil.isCurrentSession이 대조)
        redisTemplate.opsForValue().set(
                JwtUtil.SESSION_KEY_PREFIX + user.getUserId(),
                sessionId,
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
