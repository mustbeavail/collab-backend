package com.groupware.controller;

import com.groupware.config.SecurityConfig;
import com.groupware.dto.search.GlobalSearchResponse;
import com.groupware.dto.search.RoomSearchResponse;
import com.groupware.security.JwtUtil;
import com.groupware.security.UserDetailsServiceImpl;
import com.groupware.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {SearchController.class})
@Import(SecurityConfig.class)
class SearchControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private SearchService searchService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    @Test
    @WithMockUser(username = "a@test.com")
    void 글로벌검색_200() throws Exception {
        GlobalSearchResponse response = GlobalSearchResponse.builder()
                .friends(List.of(GlobalSearchResponse.FriendResult.builder()
                        .userId("bob@test.com").nickname("Bob").avatarUrl(null).build()))
                .teams(List.of())
                .rooms(List.of())
                .build();
        given(searchService.globalSearch("a@test.com", "bob")).willReturn(response);

        mockMvc.perform(get("/api/search/global").param("q", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.friends[0].nickname").value("Bob"))
                .andExpect(jsonPath("$.data.teams").isArray())
                .andExpect(jsonPath("$.data.rooms").isArray());
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 글로벌검색_빈쿼리_빈결과_200() throws Exception {
        mockMvc.perform(get("/api/search/global").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.friends").isArray())
                .andExpect(jsonPath("$.data.friends").isEmpty());
    }

    @Test
    void 글로벌검색_미인증_401() throws Exception {
        mockMvc.perform(get("/api/search/global").param("q", "test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 채팅방검색_200() throws Exception {
        RoomSearchResponse response = RoomSearchResponse.builder()
                .messages(List.of(RoomSearchResponse.MessageResult.builder()
                        .msgIdx(1L).content("안녕하세요").senderNick("Bob").sentAt("2026-06-01T12:00").build()))
                .files(List.of())
                .schedules(List.of())
                .notes(List.of())
                .build();
        given(searchService.roomSearch("a@test.com", 1L, "안녕")).willReturn(response);

        mockMvc.perform(get("/api/search/rooms/1").param("q", "안녕"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.messages[0].content").value("안녕하세요"))
                .andExpect(jsonPath("$.data.files").isArray())
                .andExpect(jsonPath("$.data.schedules").isArray())
                .andExpect(jsonPath("$.data.notes").isArray());
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 채팅방검색_빈쿼리_빈결과_200() throws Exception {
        mockMvc.perform(get("/api/search/rooms/1").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages").isEmpty());
    }

    @Test
    void 채팅방검색_미인증_401() throws Exception {
        mockMvc.perform(get("/api/search/rooms/1").param("q", "test"))
                .andExpect(status().isUnauthorized());
    }
}
