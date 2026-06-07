package com.groupware.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupware.config.SecurityConfig;
import com.groupware.dto.schedule.CreateScheduleRequest;
import com.groupware.dto.schedule.ScheduleResponse;
import com.groupware.dto.schedule.UpdateScheduleRequest;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.security.JwtUtil;
import com.groupware.security.UserDetailsServiceImpl;
import com.groupware.service.ScheduleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ScheduleController.class})
@Import(SecurityConfig.class)
class ScheduleControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ScheduleService scheduleService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    private ScheduleResponse sampleResponse() {
        return ScheduleResponse.builder()
                .scheduleIdx(1L)
                .title("팀 회의")
                .date("2026-07-01T09:00")
                .participants(List.of("Alice", "Bob"))
                .content("스프린트 논의")
                .location("회의실 A")
                .creatorId("a@test.com")
                .creatorNick("Alice")
                .build();
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 일정_목록_조회_200() throws Exception {
        given(scheduleService.getSchedulesByRoom("a@test.com", 1L))
                .willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/schedule/rooms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].scheduleIdx").value(1))
                .andExpect(jsonPath("$.data[0].title").value("팀 회의"))
                .andExpect(jsonPath("$.data[0].participants[0]").value("Alice"));
    }

    @Test
    void 일정_목록_조회_미인증_401() throws Exception {
        mockMvc.perform(get("/api/schedule/rooms/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 일정_생성_200() throws Exception {
        given(scheduleService.createSchedule(eq("a@test.com"), eq(1L), any()))
                .willReturn(sampleResponse());

        mockMvc.perform(post("/api/schedule/rooms/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "팀 회의",
                                "date", "2026-07-01T09:00",
                                "participants", List.of("Alice", "Bob"),
                                "content", "스프린트 논의",
                                "location", "회의실 A"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("팀 회의"));
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 일정_생성_제목없음_400() throws Exception {
        mockMvc.perform(post("/api/schedule/rooms/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("date", "2026-07-01T09:00"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 일정_수정_200() throws Exception {
        ScheduleResponse updated = ScheduleResponse.builder()
                .scheduleIdx(1L).title("수정 회의").date("2026-07-05T10:00")
                .participants(List.of()).content("").location("")
                .creatorId("a@test.com").creatorNick("Alice").build();

        given(scheduleService.updateSchedule(eq("a@test.com"), eq(1L), any()))
                .willReturn(updated);

        mockMvc.perform(put("/api/schedule/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "수정 회의",
                                "date", "2026-07-05T10:00"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정 회의"));
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 일정_수정_없는일정_404() throws Exception {
        willThrow(new CustomException(ErrorCode.SCHEDULE_NOT_FOUND))
                .given(scheduleService).updateSchedule(eq("a@test.com"), eq(99L), any());

        mockMvc.perform(put("/api/schedule/99").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "제목",
                                "date", "2026-07-01T09:00"
                        ))))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 일정_삭제_200() throws Exception {
        willDoNothing().given(scheduleService).deleteSchedule("a@test.com", 1L);

        mockMvc.perform(delete("/api/schedule/1").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 일정_삭제_없는일정_404() throws Exception {
        willThrow(new CustomException(ErrorCode.SCHEDULE_NOT_FOUND))
                .given(scheduleService).deleteSchedule("a@test.com", 99L);

        mockMvc.perform(delete("/api/schedule/99").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 내_전체_일정_조회_200() throws Exception {
        given(scheduleService.getMySchedules("a@test.com"))
                .willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/schedule/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].title").value("팀 회의"));
    }

    @Test
    void 내_전체_일정_조회_미인증_401() throws Exception {
        mockMvc.perform(get("/api/schedule/my"))
                .andExpect(status().isUnauthorized());
    }
}
