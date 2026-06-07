package com.groupware.controller;

import com.groupware.config.SecurityConfig;
import com.groupware.dto.file.FileResponseDto;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.security.JwtUtil;
import com.groupware.security.UserDetailsServiceImpl;
import com.groupware.service.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {FileController.class})
@Import(SecurityConfig.class)
class FileControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private FileService fileService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    private FileResponseDto sampleDto() {
        return FileResponseDto.builder()
                .fileIdx(1L)
                .oriFilename("test.pdf")
                .fileExtension("pdf")
                .fileSize(1024L)
                .createdAt(LocalDateTime.of(2026, 6, 7, 10, 0))
                .uploaderNickname("Alice")
                .uploaderId("a@test.com")
                .build();
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 파일_업로드_성공() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[100]);
        given(fileService.upload(eq("a@test.com"), eq(1L), any())).willReturn(sampleDto());

        mockMvc.perform(multipart("/api/files")
                        .file(file)
                        .param("roomIdx", "1")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.oriFilename").value("test.pdf"))
                .andExpect(jsonPath("$.data.fileIdx").value(1));
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 파일_목록_조회_성공() throws Exception {
        given(fileService.getFiles("a@test.com", 1L)).willReturn(List.of(sampleDto()));

        mockMvc.perform(get("/api/files").param("roomIdx", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].oriFilename").value("test.pdf"));
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 파일_다운로드_성공() throws Exception {
        ByteArrayResource resource = new ByteArrayResource(new byte[]{1, 2, 3}) {
            @Override public String getFilename() { return "test.pdf"; }
        };
        given(fileService.download("a@test.com", 1L)).willReturn(resource);

        mockMvc.perform(get("/api/files/1/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("test.pdf")));
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 파일_삭제_성공() throws Exception {
        willDoNothing().given(fileService).delete("a@test.com", 1L);

        mockMvc.perform(delete("/api/files/1").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 파일_삭제_권한없음_403() throws Exception {
        willThrow(new CustomException(ErrorCode.FILE_ACCESS_DENIED)).given(fileService).delete("a@test.com", 1L);

        mockMvc.perform(delete("/api/files/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "a@test.com")
    void 파일_없음_404() throws Exception {
        given(fileService.getFiles("a@test.com", 99L))
                .willThrow(new CustomException(ErrorCode.FILE_NOT_FOUND));

        mockMvc.perform(get("/api/files").param("roomIdx", "99"))
                .andExpect(status().isNotFound());
    }
}
