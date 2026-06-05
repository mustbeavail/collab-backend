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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock private UserRepository userRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiry", 604_800_000L);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
    }

    // ─── signup ───────────────────────────────────────────────────────────

    @Test
    void 회원가입_성공() {
        SignupRequest request = signupRequest("test@example.com", "password123", "테스터");
        given(userRepository.existsById("test@example.com")).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encoded-pw");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(jwtUtil.generateAccessToken(anyString())).willReturn("access-token");

        AuthResponse response = authService.signup(request);

        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getNickname()).isEqualTo("테스터");
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isNotBlank();
    }

    @Test
    void 회원가입_이메일_중복_예외() {
        SignupRequest request = signupRequest("dup@example.com", "password123", "중복유저");
        given(userRepository.existsById("dup@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
    }

    // ─── login ────────────────────────────────────────────────────────────

    @Test
    void 로그인_성공() {
        LoginRequest request = loginRequest("test@example.com", "password123");
        User user = buildUser("uid-1", "test@example.com", "encoded-pw", "테스터", null);
        given(userRepository.findById("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123", "encoded-pw")).willReturn(true);
        given(jwtUtil.generateAccessToken("uid-1")).willReturn("access-token");

        AuthResponse response = authService.login(request);

        assertThat(response.getUserId()).isEqualTo("uid-1");
        assertThat(response.getAccessToken()).isEqualTo("access-token");
    }

    @Test
    void 로그인_존재하지_않는_이메일_예외() {
        LoginRequest request = loginRequest("none@example.com", "password123");
        given(userRepository.findById("none@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_NOT_REGISTERED));
    }

    @Test
    void 로그인_비밀번호_불일치_예외() {
        LoginRequest request = loginRequest("test@example.com", "wrong-pw");
        User user = buildUser("uid-1", "test@example.com", "encoded-pw", "테스터", null);
        given(userRepository.findById("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-pw", "encoded-pw")).willReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void 로그인_탈퇴한_사용자_예외() {
        LoginRequest request = loginRequest("left@example.com", "password123");
        User user = buildUser("uid-2", "left@example.com", "encoded-pw", "탈퇴자", LocalDateTime.now().minusDays(1));
        given(userRepository.findById("left@example.com")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.WITHDRAWN_USER));
    }

    // ─── refresh ──────────────────────────────────────────────────────────

    @Test
    void 토큰_갱신_성공() {
        TokenRefreshRequest request = refreshRequest("valid-refresh-token");
        User user = buildUser("uid-1", "test@example.com", "encoded-pw", "테스터", null);
        given(valueOps.get("refresh:valid-refresh-token")).willReturn("uid-1");
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));
        given(jwtUtil.generateAccessToken("uid-1")).willReturn("new-access-token");

        AuthResponse response = authService.refresh(request);

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        verify(redisTemplate).delete("refresh:valid-refresh-token");
    }

    @Test
    void 토큰_갱신_존재하지_않는_토큰_예외() {
        TokenRefreshRequest request = refreshRequest("invalid-token");
        given(valueOps.get("refresh:invalid-token")).willReturn(null);

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND));
    }

    // ─── logout ───────────────────────────────────────────────────────────

    @Test
    void 로그아웃_시_Redis_키_삭제() {
        authService.logout("some-refresh-token");
        verify(redisTemplate).delete("refresh:some-refresh-token");
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private SignupRequest signupRequest(String email, String password, String nickname) {
        SignupRequest req = new SignupRequest();
        ReflectionTestUtils.setField(req, "email", email);
        ReflectionTestUtils.setField(req, "password", password);
        ReflectionTestUtils.setField(req, "nickname", nickname);
        return req;
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest req = new LoginRequest();
        ReflectionTestUtils.setField(req, "email", email);
        ReflectionTestUtils.setField(req, "password", password);
        return req;
    }

    private TokenRefreshRequest refreshRequest(String token) {
        TokenRefreshRequest req = new TokenRefreshRequest();
        ReflectionTestUtils.setField(req, "refreshToken", token);
        return req;
    }

    private User buildUser(String userId, String email, String pw, String nick, LocalDateTime withdrawnAt) {
        User user = new User();
        user.setUserId(userId);
        user.setPw(pw);
        user.setNick(nick);
        user.setWithdrwalAt(withdrawnAt);
        return user;
    }
}
