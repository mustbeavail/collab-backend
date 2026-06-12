package com.groupware.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupware.config.SecurityConfig;
import com.groupware.dto.friend.FriendResponse;
import com.groupware.dto.user.UserSearchResponse;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {FriendController.class, UserController.class})
@Import(SecurityConfig.class)
class FriendControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private FriendService friendService;
    @MockBean private UserService userService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    // ─── GET /api/users/search ────────────────────────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 사용자_검색_200() throws Exception {
        given(friendService.searchUsers("홍", "uid-1")).willReturn(List.of(
                UserSearchResponse.builder().userId("uid-2").nickname("홍길동").email("hong@test.com").build()
        ));

        mockMvc.perform(get("/api/users/search").param("q", "홍"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].nickname").value("홍길동"));
    }

    @Test
    void 사용자_검색_미인증_401() throws Exception {
        mockMvc.perform(get("/api/users/search").param("q", "홍"))
                .andExpect(status().isUnauthorized());
    }

    // ─── POST /api/friends/request ────────────────────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 친구_요청_성공_201() throws Exception {
        willDoNothing().given(friendService).sendRequest(any(), eq("uid-1"));

        mockMvc.perform(post("/api/friends/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetUserId", "uid-2"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 친구_요청_자기자신_400() throws Exception {
        willThrow(new CustomException(ErrorCode.CANNOT_ADD_SELF))
                .given(friendService).sendRequest(any(), eq("uid-1"));

        mockMvc.perform(post("/api/friends/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetUserId", "uid-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 친구_요청_이미존재_409() throws Exception {
        willThrow(new CustomException(ErrorCode.FRIEND_REQUEST_ALREADY_EXISTS))
                .given(friendService).sendRequest(any(), eq("uid-1"));

        mockMvc.perform(post("/api/friends/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetUserId", "uid-2"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 친구_요청_대상_누락_400() throws Exception {
        mockMvc.perform(post("/api/friends/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /api/friends ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 친구_목록_조회_200() throws Exception {
        given(friendService.getFriends("uid-1")).willReturn(List.of(
                FriendResponse.builder().friendIdx(1L).userId("uid-2").nickname("친구").email("f@test.com").status("ACCEPTED").build()
        ));

        mockMvc.perform(get("/api/friends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nickname").value("친구"));
    }

    // ─── GET /api/friends/requests ────────────────────────────────────────

    @Test
    @WithMockUser(username = "uid-2")
    void 받은_요청_목록_200() throws Exception {
        given(friendService.getPendingRequests("uid-2")).willReturn(List.of(
                FriendResponse.builder().friendIdx(1L).userId("uid-1").nickname("요청자").email("r@test.com").status("PENDING").build()
        ));

        mockMvc.perform(get("/api/friends/requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    // ─── GET /api/friends/requests/sent (qa 항목6) ───────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 보낸_요청_목록_200() throws Exception {
        given(friendService.getSentRequests("uid-1")).willReturn(List.of(
                FriendResponse.builder().friendIdx(5L).userId("uid-2").nickname("대상")
                        .email("t@test.com").status("PENDING").avatarUrl("/avatars/a.png").build()
        ));

        mockMvc.perform(get("/api/friends/requests/sent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value("uid-2"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].avatarUrl").value("/avatars/a.png"));
    }

    @Test
    void 보낸_요청_목록_미인증_401() throws Exception {
        mockMvc.perform(get("/api/friends/requests/sent"))
                .andExpect(status().isUnauthorized());
    }

    // ─── PATCH /api/friends/{idx}/accept ─────────────────────────────────

    @Test
    @WithMockUser(username = "uid-2")
    void 친구_요청_수락_200() throws Exception {
        willDoNothing().given(friendService).acceptRequest(1L, "uid-2");

        mockMvc.perform(patch("/api/friends/1/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "uid-2")
    void 친구_요청_수락_없는요청_404() throws Exception {
        willThrow(new CustomException(ErrorCode.FRIEND_REQUEST_NOT_FOUND))
                .given(friendService).acceptRequest(99L, "uid-2");

        mockMvc.perform(patch("/api/friends/99/accept"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── PATCH /api/friends/{idx}/reject ─────────────────────────────────

    @Test
    @WithMockUser(username = "uid-2")
    void 친구_요청_거절_200() throws Exception {
        willDoNothing().given(friendService).rejectRequest(1L, "uid-2");

        mockMvc.perform(patch("/api/friends/1/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ─── DELETE /api/friends/{idx} ────────────────────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 친구_삭제_200() throws Exception {
        willDoNothing().given(friendService).deleteFriend(1L, "uid-1");

        mockMvc.perform(delete("/api/friends/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "uid-1")
    void 친구_삭제_없는관계_404() throws Exception {
        willThrow(new CustomException(ErrorCode.FRIEND_NOT_FOUND))
                .given(friendService).deleteFriend(99L, "uid-1");

        mockMvc.perform(delete("/api/friends/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── GET /api/friends/online-status ──────────────────────────────────

    @Test
    @WithMockUser(username = "uid-1")
    void 친구_온라인_상태_조회_200() throws Exception {
        given(friendService.getOnlineStatuses("uid-1")).willReturn(Map.of("uid-2", true, "uid-3", false));

        mockMvc.perform(get("/api/friends/online-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.uid-2").value(true))
                .andExpect(jsonPath("$.data.uid-3").value(false));
    }

    @Test
    void 친구_온라인_상태_미인증_401() throws Exception {
        mockMvc.perform(get("/api/friends/online-status"))
                .andExpect(status().isUnauthorized());
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
