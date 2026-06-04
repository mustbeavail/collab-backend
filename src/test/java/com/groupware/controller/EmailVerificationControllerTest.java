package com.groupware.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupware.config.SecurityConfig;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.security.JwtUtil;
import com.groupware.security.UserDetailsServiceImpl;
import com.groupware.service.EmailVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmailVerificationController.class)
@Import(SecurityConfig.class)
class EmailVerificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private EmailVerificationService emailVerificationService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    // ─── POST /api/auth/email/send-code ───────────────────────────────────

    @Test
    void 코드_발송_성공_200() throws Exception {
        willDoNothing().given(emailVerificationService).sendCode(any());

        mockMvc.perform(post("/api/auth/email/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "test@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("인증코드가 발송되었습니다."));
    }

    @Test
    void 코드_발송_이메일_누락_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 코드_발송_이메일_형식_오류_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 코드_발송_메일_서버_실패_500() throws Exception {
        willThrow(new CustomException(ErrorCode.EMAIL_SEND_FAILED))
                .given(emailVerificationService).sendCode(any());

        mockMvc.perform(post("/api/auth/email/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "test@example.com"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── POST /api/auth/email/verify-code ─────────────────────────────────

    @Test
    void 코드_인증_성공_200() throws Exception {
        willDoNothing().given(emailVerificationService).verifyCode(any());

        mockMvc.perform(post("/api/auth/email/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "test@example.com", "code", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("이메일 인증이 완료되었습니다."));
    }

    @Test
    void 코드_인증_이메일_누락_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 코드_인증_코드_누락_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "test@example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 코드_인증_만료_400() throws Exception {
        willThrow(new CustomException(ErrorCode.EMAIL_CODE_EXPIRED))
                .given(emailVerificationService).verifyCode(any());

        mockMvc.perform(post("/api/auth/email/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "test@example.com", "code", "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 코드_인증_불일치_400() throws Exception {
        willThrow(new CustomException(ErrorCode.EMAIL_CODE_INVALID))
                .given(emailVerificationService).verifyCode(any());

        mockMvc.perform(post("/api/auth/email/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "test@example.com", "code", "000000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
