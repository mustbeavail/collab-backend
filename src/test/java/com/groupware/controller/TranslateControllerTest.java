package com.groupware.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupware.config.SecurityConfig;
import com.groupware.dto.translate.TranslateResponse;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.security.JwtUtil;
import com.groupware.security.UserDetailsServiceImpl;
import com.groupware.service.TranslateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {TranslateController.class})
@Import(SecurityConfig.class)
class TranslateControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private TranslateService translateService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    @Test
    @WithMockUser
    void 번역_성공_200() throws Exception {
        given(translateService.translate(any())).willReturn(new TranslateResponse("안녕하세요"));

        mockMvc.perform(post("/api/translate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("text", "Hello", "targetLang", "ko"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.translatedText").value("안녕하세요"));
    }

    @Test
    @WithMockUser
    void 빈_텍스트_400() throws Exception {
        mockMvc.perform(post("/api/translate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("text", "", "targetLang", "ko"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void targetLang_누락_400() throws Exception {
        mockMvc.perform(post("/api/translate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("text", "Hello"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void API_호출_실패_500() throws Exception {
        given(translateService.translate(any()))
                .willThrow(new CustomException(ErrorCode.TRANSLATE_FAILED));

        mockMvc.perform(post("/api/translate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("text", "Hello", "targetLang", "ko"))))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void 미인증_401() throws Exception {
        mockMvc.perform(post("/api/translate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("text", "Hello", "targetLang", "ko"))))
                .andExpect(status().isUnauthorized());
    }
}
