package com.groupware.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupware.config.SecurityConfig;
import com.groupware.dto.minutes.MeetingNoteResponse;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.security.JwtUtil;
import com.groupware.security.UserDetailsServiceImpl;
import com.groupware.service.MeetingNoteService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {MeetingNoteController.class})
@Import(SecurityConfig.class)
class MeetingNoteControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private MeetingNoteService meetingNoteService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    private MeetingNoteResponse sampleResponse() {
        return MeetingNoteResponse.builder()
                .noteIdx(1L)
                .title("스프린트 회의")
                .content("## 안건\n목표 논의")
                .createdAt("2026-06-07")
                .authorId("a@test.com")
                .authorNick("Alice")
                .build();
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 회의록_목록_조회_200() throws Exception {
        given(meetingNoteService.getNotesByRoom("a@test.com", 1L))
                .willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/minutes/rooms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].noteIdx").value(1))
                .andExpect(jsonPath("$.data[0].title").value("스프린트 회의"))
                .andExpect(jsonPath("$.data[0].authorNick").value("Alice"));
    }

    @Test
    void 회의록_목록_조회_미인증_401() throws Exception {
        mockMvc.perform(get("/api/minutes/rooms/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 회의록_생성_200() throws Exception {
        given(meetingNoteService.createNote(eq("a@test.com"), eq(1L), any()))
                .willReturn(sampleResponse());

        mockMvc.perform(post("/api/minutes/rooms/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "스프린트 회의",
                                "content", "## 안건\n목표 논의"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("스프린트 회의"));
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 회의록_생성_제목없음_400() throws Exception {
        mockMvc.perform(post("/api/minutes/rooms/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "내용만 있음"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 회의록_수정_200() throws Exception {
        MeetingNoteResponse updated = MeetingNoteResponse.builder()
                .noteIdx(1L).title("수정된 제목").content("수정된 내용")
                .createdAt("2026-06-07").authorId("a@test.com").authorNick("Alice").build();

        given(meetingNoteService.updateNote(eq("a@test.com"), eq(1L), any()))
                .willReturn(updated);

        mockMvc.perform(put("/api/minutes/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "수정된 제목",
                                "content", "수정된 내용"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 제목"));
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 회의록_수정_없는회의록_404() throws Exception {
        willThrow(new CustomException(ErrorCode.MEETING_NOTE_NOT_FOUND))
                .given(meetingNoteService).updateNote(eq("a@test.com"), eq(99L), any());

        mockMvc.perform(put("/api/minutes/99").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "제목"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 회의록_수정_권한없음_403() throws Exception {
        willThrow(new CustomException(ErrorCode.MEETING_NOTE_ACCESS_DENIED))
                .given(meetingNoteService).updateNote(eq("a@test.com"), eq(1L), any());

        mockMvc.perform(put("/api/minutes/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "수정 시도"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 회의록_삭제_200() throws Exception {
        willDoNothing().given(meetingNoteService).deleteNote("a@test.com", 1L);

        mockMvc.perform(delete("/api/minutes/1").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 회의록_삭제_없는회의록_404() throws Exception {
        willThrow(new CustomException(ErrorCode.MEETING_NOTE_NOT_FOUND))
                .given(meetingNoteService).deleteNote("a@test.com", 99L);

        mockMvc.perform(delete("/api/minutes/99").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 음성_AI_회의록_생성_200() throws Exception {
        given(meetingNoteService.generateVoiceMinutes(eq("a@test.com"), eq(1L), any()))
                .willReturn(sampleResponse());

        MockMultipartFile audioFile = new MockMultipartFile(
                "audio", "recording.webm", "audio/webm", "fake-audio".getBytes());

        mockMvc.perform(multipart("/api/minutes/rooms/1/voice-generate")
                        .file(audioFile)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.noteIdx").value(1));
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 음성_AI_회의록_생성_파일없음_400() throws Exception {
        willThrow(new CustomException(ErrorCode.NO_AUDIO_FILES))
                .given(meetingNoteService).generateVoiceMinutes(eq("a@test.com"), eq(1L), any());

        MockMultipartFile emptyFile = new MockMultipartFile(
                "audio", "rec.webm", "audio/webm", new byte[0]);

        mockMvc.perform(multipart("/api/minutes/rooms/1/voice-generate")
                        .file(emptyFile)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
