package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.Message;
import com.groupware.domain.UploadFile;
import com.groupware.domain.User;
import com.groupware.dto.chat.ChatMessagePayload;
import com.groupware.dto.file.FileResponseDto;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.MessageRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.TeamMemberRepository;
import com.groupware.repository.UploadFileRepository;
import com.groupware.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.Spy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private MessageRepository messageRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private DemoSessionStore demoSessionStore;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

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
        given(uploadFileRepository.findNormalFilesByChatRoom(room)).willReturn(List.of(f));

        List<FileResponseDto> result = fileService.getFiles("a@test.com", 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOriFilename()).isEqualTo("test.pdf");
    }

    @Test
    void 파일_삭제_성공() {
        UploadFile f = makeUploadFile(1L);
        given(uploadFileRepository.findById(1L)).willReturn(Optional.of(f));
        given(messageRepository.findFileMessagesByRoomAndFileIdx(any(), any())).willReturn(List.of());

        fileService.delete("a@test.com", 1L);

        verify(uploadFileRepository).delete(f);
    }

    @Test
    void 파일_삭제시_FILE메시지_삭제표시_및_FILE_DELETED_브로드캐스트() {
        UploadFile f = makeUploadFile(6L);
        Message fileMsg = new Message();
        ReflectionTestUtils.setField(fileMsg, "msgIdx", 30L);
        ReflectionTestUtils.setField(fileMsg, "chatRoom", room);
        ReflectionTestUtils.setField(fileMsg, "msgType", "FILE");
        ReflectionTestUtils.setField(fileMsg, "content",
                "{\"fileIdx\":6,\"oriFilename\":\"x.txt\",\"fileSize\":19,\"fileExtension\":\"txt\"}");
        ReflectionTestUtils.setField(fileMsg, "delYn", false);

        given(uploadFileRepository.findById(6L)).willReturn(Optional.of(f));
        given(messageRepository.findFileMessagesByRoomAndFileIdx(room, "%\"fileIdx\":6,%"))
                .willReturn(List.of(fileMsg));

        fileService.delete("a@test.com", 6L);

        // 메시지는 남고 content에 deleted=true 마킹
        assertThat(fileMsg.getContent()).contains("\"deleted\":true");
        verify(messageRepository).save(fileMsg);
        // FILE_DELETED 브로드캐스트
        ArgumentCaptor<ChatMessagePayload> captor = ArgumentCaptor.forClass(ChatMessagePayload.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/room/1"), captor.capture());
        assertThat(captor.getValue().getMsgType()).isEqualTo("FILE_DELETED");
        assertThat(captor.getValue().getContent()).isEqualTo("6");
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

    // ───────────────────────── [I] 녹음 파일 관리 ─────────────────────────

    @Test
    void 녹음_업로드_성공_RECORDING타입_30일만료_채팅메시지미생성() {
        MockMultipartFile rec = new MockMultipartFile(
                "file", "녹음.webm", "audio/webm", new byte[200]);

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);
        given(uploadFileRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        FileResponseDto result = fileService.uploadRecording("a@test.com", 1L, rec);

        ArgumentCaptor<UploadFile> captor = ArgumentCaptor.forClass(UploadFile.class);
        verify(uploadFileRepository).save(captor.capture());
        UploadFile saved = captor.getValue();
        assertThat(saved.getFileType()).isEqualTo(FileService.RECORDING_FILE_TYPE);
        assertThat(saved.getExpiresAt()).isNotNull();
        // 만료일이 약 30일 뒤(±1일 허용)
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));
        assertThat(saved.getExpiresAt()).isBefore(LocalDateTime.now().plusDays(31));
        // 녹음은 채팅 메시지를 만들지 않는다
        verify(messageRepository, org.mockito.Mockito.never()).save(any());
        assertThat(result.getExpiresAt()).isNotNull();
    }

    @Test
    void 녹음_50MB_초과_실패() {
        byte[] big = new byte[(int)(50L * 1024 * 1024 + 1)];
        MockMultipartFile rec = new MockMultipartFile("file", "big.webm", "audio/webm", big);

        assertThatThrownBy(() -> fileService.uploadRecording("a@test.com", 1L, rec))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);
    }

    @Test
    void 녹음_목록_조회_만료전만() {
        UploadFile r = makeUploadFile(5L);
        ReflectionTestUtils.setField(r, "expiresAt", LocalDateTime.now().plusDays(10));
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);
        given(uploadFileRepository.findActiveRecordingsByChatRoom(eq(room), any())).willReturn(List.of(r));

        List<FileResponseDto> result = fileService.getRecordings("a@test.com", 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFileIdx()).isEqualTo(5L);
        // 일반 파일 목록 쿼리는 호출되지 않음(녹음 전용 쿼리만)
        verify(uploadFileRepository).findActiveRecordingsByChatRoom(eq(room), any());
    }

    @Test
    void 만료녹음_정리_물리파일_레코드_삭제() {
        UploadFile expired = makeUploadFile(7L);
        ReflectionTestUtils.setField(expired, "expiresAt", LocalDateTime.now().minusDays(1));
        given(uploadFileRepository.findExpiredRecordings(any())).willReturn(List.of(expired));

        int count = fileService.deleteExpiredRecordings();

        assertThat(count).isEqualTo(1);
        verify(uploadFileRepository).deleteAll(List.of(expired));
    }
}
