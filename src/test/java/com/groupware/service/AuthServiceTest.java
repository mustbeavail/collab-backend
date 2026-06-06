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
import jakarta.persistence.Query;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    @Mock private EntityManager entityManager;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiry", 604_800_000L);
        ReflectionTestUtils.setField(authService, "entityManager", entityManager);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
    }

    // ─── signup ───────────────────────────────────────────────────────────

    @Test
    void 회원가입_성공() {
        SignupRequest request = signupRequest("test@example.com", "password123", "테스터", "안녕하세요");
        given(userRepository.findById("test@example.com")).willReturn(Optional.empty());
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
    void 회원가입_about_저장() {
        SignupRequest request = signupRequest("test@example.com", "password123", "테스터", "자기소개입니다");
        given(userRepository.findById("test@example.com")).willReturn(Optional.empty());
        given(passwordEncoder.encode(anyString())).willReturn("encoded-pw");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(jwtUtil.generateAccessToken(anyString())).willReturn("access-token");

        authService.signup(request);

        verify(userRepository).save(any(User.class));
    }

    @Test
    void 회원가입_이메일_중복_예외() {
        SignupRequest request = signupRequest("dup@example.com", "password123", "중복유저", null);
        User activeUser = buildUser("dup@example.com", "encoded-pw", "중복유저", null);
        given(userRepository.findById("dup@example.com")).willReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @Test
    void 탈퇴한_회원_재가입_성공() {
        User withdrawn = buildUser("left@example.com", "old-pw", "이전닉네임", LocalDateTime.now().minusDays(7));
        SignupRequest request = signupRequest("left@example.com", "newpassword123", "새닉네임", null);

        given(userRepository.findById("left@example.com")).willReturn(Optional.of(withdrawn));
        given(userRepository.existsById("left@example.com_1")).willReturn(false);

        Query mockNativeQuery = mock(Query.class);
        given(entityManager.createNativeQuery(anyString())).willReturn(mockNativeQuery);
        given(mockNativeQuery.setParameter(anyString(), any())).willReturn(mockNativeQuery);
        given(mockNativeQuery.executeUpdate()).willReturn(1);

        given(passwordEncoder.encode(anyString())).willReturn("encoded-pw");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(jwtUtil.generateAccessToken(anyString())).willReturn("access-token");

        AuthResponse response = authService.signup(request);

        assertThat(response.getEmail()).isEqualTo("left@example.com");
        assertThat(response.getNickname()).isEqualTo("새닉네임");
    }

    // ─── checkEmail ───────────────────────────────────────────────────────

    @Test
    void 이메일_중복체크_사용가능() {
        given(userRepository.existsByUserIdAndWithdrwalAtIsNull("new@example.com")).willReturn(false);
        authService.checkEmail("new@example.com"); // 예외 없이 통과
    }

    @Test
    void 이메일_중복체크_중복_예외() {
        given(userRepository.existsByUserIdAndWithdrwalAtIsNull("dup@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.checkEmail("dup@example.com"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @Test
    void 이메일_중복체크_탈퇴회원은_사용가능() {
        given(userRepository.existsByUserIdAndWithdrwalAtIsNull("withdrawn@example.com")).willReturn(false);
        authService.checkEmail("withdrawn@example.com"); // 예외 없이 통과
    }

    // ─── checkNickname ────────────────────────────────────────────────────

    @Test
    void 닉네임_중복체크_사용가능() {
        given(userRepository.existsByNickAndWithdrwalAtIsNull("새닉네임")).willReturn(false);
        authService.checkNickname("새닉네임"); // 예외 없이 통과
    }

    @Test
    void 닉네임_중복체크_중복_예외() {
        given(userRepository.existsByNickAndWithdrwalAtIsNull("중복닉네임")).willReturn(true);

        assertThatThrownBy(() -> authService.checkNickname("중복닉네임"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NICKNAME_ALREADY_EXISTS));
    }

    // ─── login ────────────────────────────────────────────────────────────

    @Test
    void 로그인_성공() {
        LoginRequest request = loginRequest("test@example.com", "password123");
        User user = buildUser("test@example.com", "encoded-pw", "테스터", null);
        given(userRepository.findById("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123", "encoded-pw")).willReturn(true);
        given(jwtUtil.generateAccessToken("test@example.com")).willReturn("access-token");

        AuthResponse response = authService.login(request);

        assertThat(response.getUserId()).isEqualTo("test@example.com");
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
        User user = buildUser("test@example.com", "encoded-pw", "테스터", null);
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
        User user = buildUser("left@example.com", "encoded-pw", "탈퇴자", LocalDateTime.now().minusDays(1));
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
        User user = buildUser("test@example.com", "encoded-pw", "테스터", null);
        given(valueOps.get("refresh:valid-refresh-token")).willReturn("test@example.com");
        given(userRepository.findById("test@example.com")).willReturn(Optional.of(user));
        given(jwtUtil.generateAccessToken("test@example.com")).willReturn("new-access-token");

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

    // ─── 중복 로그인 방지 ─────────────────────────────────────────────────

    @Test
    void 로그인_기존_세션_있으면_강제_로그아웃_이벤트_전송() {
        LoginRequest request = loginRequest("test@example.com", "password123");
        User user = buildUser("test@example.com", "encoded-pw", "테스터", null);
        given(userRepository.findById("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123", "encoded-pw")).willReturn(true);
        given(jwtUtil.generateAccessToken("test@example.com")).willReturn("access-token");
        given(valueOps.get("user:test@example.com")).willReturn("old-refresh-token");

        authService.login(request);

        verify(redisTemplate).delete("refresh:old-refresh-token");
        verify(messagingTemplate).convertAndSendToUser(
                eq("test@example.com"), eq("/queue/session"), eq(Map.of("type", "FORCE_LOGOUT")));
    }

    @Test
    void 로그인_기존_세션_없으면_강제_로그아웃_이벤트_미전송() {
        LoginRequest request = loginRequest("test@example.com", "password123");
        User user = buildUser("test@example.com", "encoded-pw", "테스터", null);
        given(userRepository.findById("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123", "encoded-pw")).willReturn(true);
        given(jwtUtil.generateAccessToken("test@example.com")).willReturn("access-token");
        given(valueOps.get("user:test@example.com")).willReturn(null);

        authService.login(request);

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    // ─── logout ───────────────────────────────────────────────────────────

    @Test
    void 로그아웃_시_Redis_키_삭제() {
        given(valueOps.get("refresh:some-refresh-token")).willReturn(null);

        authService.logout("some-refresh-token");

        verify(redisTemplate).delete("refresh:some-refresh-token");
    }

    @Test
    void 로그아웃_시_역방향_매핑도_삭제() {
        given(valueOps.get("refresh:my-refresh-token")).willReturn("test@example.com");
        given(valueOps.get("user:test@example.com")).willReturn("my-refresh-token");

        authService.logout("my-refresh-token");

        verify(redisTemplate).delete("refresh:my-refresh-token");
        verify(redisTemplate).delete("user:test@example.com");
    }

    @Test
    void 로그아웃_시_다른_세션의_역방향_매핑은_삭제_안함() {
        given(valueOps.get("refresh:old-refresh-token")).willReturn("test@example.com");
        // 역방향 매핑이 이미 새 세션으로 갱신된 상태
        given(valueOps.get("user:test@example.com")).willReturn("new-refresh-token");

        authService.logout("old-refresh-token");

        verify(redisTemplate).delete("refresh:old-refresh-token");
        verify(redisTemplate, never()).delete("user:test@example.com");
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private SignupRequest signupRequest(String email, String password, String nickname, String about) {
        SignupRequest req = new SignupRequest();
        ReflectionTestUtils.setField(req, "email", email);
        ReflectionTestUtils.setField(req, "password", password);
        ReflectionTestUtils.setField(req, "nickname", nickname);
        ReflectionTestUtils.setField(req, "about", about);
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

    private User buildUser(String userId, String pw, String nick, LocalDateTime withdrawnAt) {
        User user = new User();
        user.setUserId(userId);
        user.setPw(pw);
        user.setNick(nick);
        user.setWithdrwalAt(withdrawnAt);
        return user;
    }
}
