package com.groupware.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupware.config.SecurityConfig;
import com.groupware.dto.user.ChangePasswordRequest;
import com.groupware.dto.user.UpdateProfileRequest;
import com.groupware.dto.user.UserProfileResponse;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.security.JwtUtil;
import com.groupware.security.UserDetailsServiceImpl;
import com.groupware.service.FriendService;
import com.groupware.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {UserController.class})
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private UserService userService;
    @MockBean private FriendService friendService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    // ─── GET /api/users/me ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 내_프로필_조회_200() throws Exception {
        UserProfileResponse profile = UserProfileResponse.builder()
                .userId("uid-1")
                .email("test@test.com")
                .nickname("테스터")
                .about("안녕하세요")
                .joinAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
        given(userService.getMyProfile("uid-1")).willReturn(profile);

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("uid-1"))
                .andExpect(jsonPath("$.data.email").value("test@test.com"))
                .andExpect(jsonPath("$.data.nickname").value("테스터"))
                .andExpect(jsonPath("$.data.about").value("안녕하세요"));
    }

    @Test
    void 내_프로필_조회_미인증_401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "uid-x")
    void 내_프로필_조회_사용자없음_404() throws Exception {
        given(userService.getMyProfile("uid-x"))
                .willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 내_프로필_조회_탈퇴한사용자_403() throws Exception {
        given(userService.getMyProfile("uid-1"))
                .willThrow(new CustomException(ErrorCode.WITHDRAWN_USER));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── PUT /api/users/me ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 프로필_수정_200() throws Exception {
        UserProfileResponse updated = UserProfileResponse.builder()
                .userId("uid-1")
                .email("test@test.com")
                .nickname("새닉네임")
                .about("새소개")
                .joinAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
        given(userService.updateMyProfile(eq("uid-1"), any(UpdateProfileRequest.class))).willReturn(updated);

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nickname", "새닉네임", "about", "새소개"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
                .andExpect(jsonPath("$.data.about").value("새소개"));
    }

    @Test
    void 프로필_수정_미인증_401() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nickname", "닉네임"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 프로필_수정_닉네임_누락_400() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 프로필_수정_탈퇴한사용자_403() throws Exception {
        given(userService.updateMyProfile(eq("uid-1"), any(UpdateProfileRequest.class)))
                .willThrow(new CustomException(ErrorCode.WITHDRAWN_USER));

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nickname", "닉네임"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── PUT /api/users/me/password ───────────────────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 비밀번호_변경_200() throws Exception {
        willDoNothing().given(userService).changePassword(eq("uid-1"), any(ChangePasswordRequest.class));

        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "oldPass1!", "newPassword", "newPass1!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("비밀번호가 변경되었습니다."));
    }

    @Test
    void 비밀번호_변경_미인증_401() throws Exception {
        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "old", "newPassword", "newPass1!"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 비밀번호_변경_현재비밀번호_누락_400() throws Exception {
        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newPassword", "newPass1!"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 비밀번호_변경_짧은_새비밀번호_허용_200() throws Exception {
        // 비번 길이/복잡도 제한 제거됨: 짧은 새 비번도 변경 가능
        willDoNothing().given(userService).changePassword(eq("uid-1"), any(ChangePasswordRequest.class));

        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "oldPass1!", "newPassword", "123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 비밀번호_변경_현재비밀번호_불일치_400() throws Exception {
        willThrow(new CustomException(ErrorCode.WRONG_CURRENT_PASSWORD))
                .given(userService).changePassword(eq("uid-1"), any(ChangePasswordRequest.class));

        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "wrongPass!", "newPassword", "newPass1!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── POST /api/users/me/avatar ────────────────────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 아바타_업로드_200() throws Exception {
        given(userService.uploadAvatar(eq("uid-1"), any())).willReturn("/avatars/uuid.jpg");

        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "photo.jpg", "image/jpeg", "img".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.avatarUrl").value("/avatars/uuid.jpg"));
    }

    @Test
    void 아바타_업로드_미인증_401() throws Exception {
        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "photo.jpg", "image/jpeg", "img".getBytes())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 아바타_업로드_이미지_아닌_파일_400() throws Exception {
        given(userService.uploadAvatar(eq("uid-1"), any()))
                .willThrow(new CustomException(ErrorCode.INVALID_FILE_TYPE));

        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "doc.pdf", "application/pdf", "data".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── DELETE /api/users/me ─────────────────────────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 회원_탈퇴_200() throws Exception {
        willDoNothing().given(userService).withdraw("uid-1");

        mockMvc.perform(delete("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("회원 탈퇴가 완료되었습니다."));
    }

    @Test
    void 회원_탈퇴_미인증_401() throws Exception {
        mockMvc.perform(delete("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 회원_탈퇴_이미_탈퇴한사용자_403() throws Exception {
        willThrow(new CustomException(ErrorCode.WITHDRAWN_USER))
                .given(userService).withdraw("uid-1");

        mockMvc.perform(delete("/api/users/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── GET /api/users/{userId} ──────────────────────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 공개_프로필_조회_200() throws Exception {
        UserProfileResponse profile = UserProfileResponse.builder()
                .userId("uid-2").email("other@test.com").nickname("다른유저")
                .joinAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build();
        given(userService.getUserPublicProfile("uid-2")).willReturn(profile);

        mockMvc.perform(get("/api/users/uid-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("uid-2"))
                .andExpect(jsonPath("$.data.nickname").value("다른유저"));
    }

    @Test
    void 공개_프로필_조회_미인증_401() throws Exception {
        mockMvc.perform(get("/api/users/uid-2"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 공개_프로필_조회_사용자없음_404() throws Exception {
        given(userService.getUserPublicProfile("uid-x"))
                .willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/api/users/uid-x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── DELETE /api/users/me/avatar ──────────────────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 아바타_제거_200() throws Exception {
        willDoNothing().given(userService).deleteAvatar("uid-1");

        mockMvc.perform(delete("/api/users/me/avatar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("프로필 사진이 제거되었습니다."));
    }

    @Test
    void 아바타_제거_미인증_401() throws Exception {
        mockMvc.perform(delete("/api/users/me/avatar"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /api/users/check-nickname ────────────────────────────────────
    // 회귀방지: /{userId} 보다 먼저 매칭되어야 함(과거 /check-nickname 이 {userId}로 잡혀 USER_NOT_FOUND 발생)

    @Test
    @WithMockUser(username = "uid-1")
    void 닉네임_중복확인_사용가능_200() throws Exception {
        given(userService.isNicknameAvailable("uid-1", "새닉네임")).willReturn(true);

        mockMvc.perform(get("/api/users/check-nickname").param("nickname", "새닉네임"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 닉네임_중복확인_중복_200_available_false() throws Exception {
        given(userService.isNicknameAvailable("uid-1", "중복닉")).willReturn(false);

        mockMvc.perform(get("/api/users/check-nickname").param("nickname", "중복닉"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    void 닉네임_중복확인_미인증_401() throws Exception {
        mockMvc.perform(get("/api/users/check-nickname").param("nickname", "닉"))
                .andExpect(status().isUnauthorized());
    }

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
