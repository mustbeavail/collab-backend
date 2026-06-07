package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.UploadFile;
import com.groupware.domain.User;
import com.groupware.dto.file.FileResponseDto;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.TeamMemberRepository;
import com.groupware.repository.UploadFileRepository;
import com.groupware.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @InjectMocks private FileService fileService;
    @Mock private UploadFileRepository uploadFileRepository;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private UserRepository userRepository;

    @TempDir Path tempDir;

    private User user;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fileService, "uploadDir", tempDir.toString());

        user = new User();
        ReflectionTestUtils.setField(user, "userId", "a@test.com");
        ReflectionTestUtils.setField(user, "nick", "Alice");

        room = new ChatRoom();
        ReflectionTestUtils.setField(room, "roomIdx", 1L);
    }

    private UploadFile makeUploadFile(Long idx) {
        UploadFile f = new UploadFile();
        ReflectionTestUtils.setField(f, "fileIdx", idx);
        ReflectionTestUtils.setField(f, "user", user);
        ReflectionTestUtils.setField(f, "chatRoom", room);
        ReflectionTestUtils.setField(f, "oriFilename", "test.pdf");
        ReflectionTestUtils.setField(f, "newFilename", "uuid.pdf");
        ReflectionTestUtils.setField(f, "filePath", "/files/uuid.pdf");
        ReflectionTestUtils.setField(f, "fileExtension", "pdf");
        ReflectionTestUtils.setField(f, "fileType", "application/pdf");
        ReflectionTestUtils.setField(f, "fileSize", 1024L);
        ReflectionTestUtils.setField(f, "createdAt", LocalDateTime.now());
        return f;
    }

    @Test
    void 파일_업로드_성공() {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", new byte[100]);

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);

        UploadFile saved = makeUploadFile(1L);
        given(uploadFileRepository.save(any())).willReturn(saved);

        FileResponseDto result = fileService.upload("a@test.com", 1L, multipartFile);

        assertThat(result.getOriFilename()).isEqualTo("test.pdf");
        verify(uploadFileRepository).save(any());
    }

    @Test
    void 파일_업로드_50MB_초과_실패() {
        byte[] bigFile = new byte[(int)(50L * 1024 * 1024 + 1)];
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "big.zip", "application/zip", bigFile);

        assertThatThrownBy(() -> fileService.upload("a@test.com", 1L, multipartFile))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);
    }

    @Test
    void 파일_목록_조회_성공() {
        UploadFile f = makeUploadFile(1L);
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);
        given(uploadFileRepository.findByChatRoomOrderByCreatedAtDesc(room)).willReturn(List.of(f));

        List<FileResponseDto> result = fileService.getFiles("a@test.com", 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOriFilename()).isEqualTo("test.pdf");
    }

    @Test
    void 파일_삭제_성공() {
        UploadFile f = makeUploadFile(1L);
        given(uploadFileRepository.findById(1L)).willReturn(Optional.of(f));

        fileService.delete("a@test.com", 1L);

        verify(uploadFileRepository).delete(f);
    }

    @Test
    void 파일_삭제_권한_없음() {
        UploadFile f = makeUploadFile(1L);
        given(uploadFileRepository.findById(1L)).willReturn(Optional.of(f));

        assertThatThrownBy(() -> fileService.delete("other@test.com", 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_ACCESS_DENIED);
    }

    @Test
    void 파일_없을때_삭제_실패() {
        given(uploadFileRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.delete("a@test.com", 99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_NOT_FOUND);
    }
}
