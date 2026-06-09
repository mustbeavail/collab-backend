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
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
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
    @Mock private EmailVerificationService emailVerificationService;

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
        org.mockito.Mockito.doNothing().when(emailVerificationService).consumeVerified("test@example.com");
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
        org.mockito.Mockito.doNothing().when(emailVerificationService).consumeVerified("test@example.com");
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
        org.mockito.Mockito.doNothing().when(emailVerificationService).consumeVerified("dup@example.com");
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
        org.mockito.Mockito.doNothing().when(emailVerificationService).consumeVerified("left@example.com");

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

    @Test
    void 회원가입_닉네임_중복_예외() {
        SignupRequest request = signupRequest("new@example.com", "password123", "중복닉", null);
        given(userRepository.findById("new@example.com")).willReturn(Optional.empty());
        given(userRepository.existsByNickAndWithdrawalAtIsNull("중복닉")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NICKNAME_ALREADY_EXISTS));

        verify(userRepository, never()).save(any(User.class));
        verify(emailVerificationService, never()).consumeVerified(anyString());
    }

    @Test
    void 회원가입_이메일_미인증_예외() {
        SignupRequest request = signupRequest("new@example.com", "password123", "닉", null);
        org.mockito.Mockito.doThrow(new CustomException(ErrorCode.EMAIL_NOT_VERIFIED))
                .when(emailVerificationService).requireVerified("new@example.com");

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));

        verify(userRepository, never()).save(any(User.class));
        verify(emailVerificationService, never()).consumeVerified(anyString());
    }

    @Test
    void 회원가입_인증키_소비는_저장_성공_후() {
        SignupRequest request = signupRequest("ok@example.com", "password123", "굿닉", null);
        given(userRepository.findById("ok@example.com")).willReturn(Optional.empty());
        given(userRepository.existsByNickAndWithdrawalAtIsNull("굿닉")).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encoded-pw");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(jwtUtil.generateAccessToken(anyString())).willReturn("access-token");

        authService.signup(request);

        // 검증은 맨 앞, 소비(삭제)는 save 성공 후 순서
        InOrder order = inOrder(emailVerificationService, userRepository);
        order.verify(emailVerificationService).requireVerified("ok@example.com");
        order.verify(userRepository).save(any(User.class));
        order.verify(emailVerificationService).consumeVerified("ok@example.com");
    }

    @Test
    void 재가입_긴_이메일_rename_id_254자_이하() {
        String longEmail = "x".repeat(247) + "@ex.com"; // 254자 = User.userId 컬럼 최대
        User withdrawn = buildUser(longEmail, "old-pw", "이전닉", LocalDateTime.now().minusDays(1));
        SignupRequest request = signupRequest(longEmail, "newpassword123", "새닉", null);
        org.mockito.Mockito.doNothing().when(emailVerificationService).requireVerified(longEmail);
        given(userRepository.findById(longEmail)).willReturn(Optional.of(withdrawn));
        given(userRepository.existsById(anyString())).willReturn(false);
        given(userRepository.existsByNickAndWithdrawalAtIsNull("새닉")).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encoded-pw");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(jwtUtil.generateAccessToken(anyString())).willReturn("access-token");

        Query nativeQuery = mock(Query.class);
        given(entityManager.createNativeQuery(anyString())).willReturn(nativeQuery);
        ArgumentCaptor<Object> valCaptor = ArgumentCaptor.forClass(Object.class);
        given(nativeQuery.setParameter(anyString(), valCaptor.capture())).willReturn(nativeQuery);
        given(nativeQuery.executeUpdate()).willReturn(1);

        authService.signup(request);

        // renameWithdrawnUser 의 첫 setParameter("newId", ...) 값이 254자를 넘지 않아야 함
        String newId = (String) valCaptor.getAllValues().get(0);
        assertThat(newId.length()).isLessThanOrEqualTo(254);
        assertThat(newId).endsWith("_1");
    }

    // ─── checkEmail ───────────────────────────────────────────────────────

    @Test
    void 이메일_중복체크_사용가능() {
        given(userRepository.existsByUserIdAndWithdrawalAtIsNull("new@example.com")).willReturn(false);
        authService.checkEmail("new@example.com"); // 예외 없이 통과
    }

    @Test
    void 이메일_중복체크_중복_예외() {
        given(userRepository.existsByUserIdAndWithdrawalAtIsNull("dup@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.checkEmail("dup@example.com"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @Test
    void 이메일_중복체크_탈퇴회원은_사용가능() {
        given(userRepository.existsByUserIdAndWithdrawalAtIsNull("withdrawn@example.com")).willReturn(false);
        authService.checkEmail("withdrawn@example.com"); // 예외 없이 통과
    }

    // ─── checkNickname ────────────────────────────────────────────────────

    @Test
    void 닉네임_중복체크_사용가능() {
        given(userRepository.existsByNickAndWithdrawalAtIsNull("새닉네임")).willReturn(false);
        authService.checkNickname("새닉네임", null); // 예외 없이 통과
    }

    @Test
    void 닉네임_중복체크_중복_예외() {
        given(userRepository.existsByNickAndWithdrawalAtIsNull("중복닉네임")).willReturn(true);

        assertThatThrownBy(() -> authService.checkNickname("중복닉네임", null))
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
        User user = buildUser("test@example.com", "encoded-pw", "테스터", null);
        given(valueOps.get("refresh:valid-refresh-token")).willReturn("test@example.com");
        given(userRepository.findById("test@example.com")).willReturn(Optional.of(user));
        given(jwtUtil.generateAccessToken("test@example.com")).willReturn("new-access-token");

        AuthResponse response = authService.refresh("valid-refresh-token");

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        verify(redisTemplate).delete("refresh:valid-refresh-token");
    }

    @Test
    void 토큰_갱신_존재하지_않는_토큰_예외() {
        given(valueOps.get("refresh:invalid-token")).willReturn(null);

        assertThatThrownBy(() -> authService.refresh("invalid-token"))
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

        authService.logout("some-refresh-token", null);

        verify(redisTemplate).delete("refresh:some-refresh-token");
    }

    @Test
    void 로그아웃_시_역방향_매핑도_삭제() {
        given(valueOps.get("refresh:my-refresh-token")).willReturn("test@example.com");
        given(valueOps.get("user:test@example.com")).willReturn("my-refresh-token");

        authService.logout("my-refresh-token", null);

        verify(redisTemplate).delete("refresh:my-refresh-token");
        verify(redisTemplate).delete("user:test@example.com");
    }

    @Test
    void 로그아웃_시_다른_세션의_역방향_매핑은_삭제_안함() {
        given(valueOps.get("refresh:old-refresh-token")).willReturn("test@example.com");
        // 역방향 매핑이 이미 새 세션으로 갱신된 상태
        given(valueOps.get("user:test@example.com")).willReturn("new-refresh-token");

        authService.logout("old-refresh-token", null);

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

    private User buildUser(String userId, String pw, String nick, LocalDateTime withdrawnAt) {
        User user = new User();
        user.setUserId(userId);
        user.setPw(pw);
        user.setNick(nick);
        user.setWithdrawalAt(withdrawnAt);
        return user;
    }
}
