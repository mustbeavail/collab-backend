package com.groupware.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupware.config.SecurityConfig;
import com.groupware.dto.auth.AuthResponse;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.security.JwtUtil;
import com.groupware.security.UserDetailsServiceImpl;
import com.groupware.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthService authService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    // ─── POST /api/auth/signup ─────────────────────────────────────────────

    @Test
    void 회원가입_성공_201() throws Exception {
        AuthResponse stubResponse = authResponse("uid-1", "test@example.com", "테스터");
        given(authService.signup(any())).willReturn(stubResponse);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "test@example.com",
                                "password", "Test1234!",
                                "nickname", "테스터"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("테스터"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    void 회원가입_이메일_누락_400() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "password", "Test1234!",
                                "nickname", "테스터"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 회원가입_이메일_형식_오류_400() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "not-an-email",
                                "password", "Test1234!",
                                "nickname", "테스터"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 회원가입_짧고_단순한_비밀번호_허용_201() throws Exception {
        // 비번 길이/숫자/특수문자 제한 제거됨: 짧고 단순한 비번도 가입 가능
        given(authService.signup(any())).willReturn(authResponse("uid-1", "test@example.com", "테스터"));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "test@example.com",
                                "password", "123",
                                "nickname", "테스터"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 회원가입_이메일_중복_409() throws Exception {
        given(authService.signup(any())).willThrow(new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "dup@example.com",
                                "password", "Test1234!",
                                "nickname", "중복유저"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── POST /api/auth/login ──────────────────────────────────────────────

    @Test
    void 로그인_성공_200() throws Exception {
        AuthResponse stubResponse = authResponse("uid-1", "test@example.com", "테스터");
        given(authService.login(any())).willReturn(stubResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "test@example.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("uid-1"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    void 로그인_시_refreshToken_쿠키_HttpOnly_SameSite_Lax_local() throws Exception {
        AuthResponse stub = AuthResponse.builder()
                .accessToken("access-token").userId("uid-1").email("test@example.com")
                .nickname("테스터").refreshToken("rt-123").build();
        given(authService.login(any())).willReturn(stub);

        var mvcResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "test@example.com", "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        // Cookie 객체로 직접 검증 (MockHttpServletResponse는 Set-Cookie 헤더에 SameSite를 직렬화하지 않음)
        Cookie refreshCookie = mvcResult.getResponse().getCookie("refreshToken");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getValue()).isEqualTo("rt-123");
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.getSecure()).isFalse();              // local 기본: secure=false
        assertThat(refreshCookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(refreshCookie.getPath()).isEqualTo("/api/auth/refresh");
    }

    @Test
    void 로그인_잘못된_자격증명_401() throws Exception {
        given(authService.login(any())).willThrow(new CustomException(ErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "test@example.com",
                                "password", "wrong-pw"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 로그인_탈퇴한_사용자_403() throws Exception {
        given(authService.login(any())).willThrow(new CustomException(ErrorCode.WITHDRAWN_USER));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "left@example.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── POST /api/auth/refresh ────────────────────────────────────────────

    @Test
    void 토큰_갱신_성공_200() throws Exception {
        AuthResponse stubResponse = authResponse("uid-1", "test@example.com", "테스터");
        given(authService.refresh(anyString())).willReturn(stubResponse);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", "valid-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    void 토큰_갱신_쿠키_없으면_401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 토큰_갱신_유효하지_않은_토큰_401() throws Exception {
        given(authService.refresh(anyString())).willThrow(new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", "invalid-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── POST /api/auth/logout ─────────────────────────────────────────────

    @Test
    void 로그아웃_성공_200() throws Exception {
        willDoNothing().given(authService).logout(any(), any());

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("refreshToken", "some-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("로그아웃되었습니다."));
    }

    // ─── GET /api/auth/check-email ────────────────────────────────────────

    @Test
    void 이메일_중복체크_사용가능_200() throws Exception {
        willDoNothing().given(authService).checkEmail(anyString());

        mockMvc.perform(get("/api/auth/check-email").param("email", "new@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("사용 가능한 이메일입니다."));
    }

    @Test
    void 이메일_중복체크_중복_409() throws Exception {
        willThrow(new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS))
                .given(authService).checkEmail(anyString());

        mockMvc.perform(get("/api/auth/check-email").param("email", "dup@example.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── GET /api/auth/check-nickname ─────────────────────────────────────

    @Test
    void 닉네임_중복체크_사용가능_200() throws Exception {
        willDoNothing().given(authService).checkNickname(anyString(), any());

        mockMvc.perform(get("/api/auth/check-nickname").param("nickname", "새닉네임"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("사용 가능한 닉네임입니다."));
    }

    @Test
    void 닉네임_중복체크_중복_409() throws Exception {
        willThrow(new CustomException(ErrorCode.NICKNAME_ALREADY_EXISTS))
                .given(authService).checkNickname(anyString(), any());

        mockMvc.perform(get("/api/auth/check-nickname").param("nickname", "중복닉네임"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    private AuthResponse authResponse(String userId, String email, String nickname) {
        return AuthResponse.builder()
                .accessToken("access-token")
                .userId(userId)
                .email(email)
                .nickname(nickname)
                .build();
    }
}
