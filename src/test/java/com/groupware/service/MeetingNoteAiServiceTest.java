package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.MeetingNote;
import com.groupware.domain.Message;
import com.groupware.domain.User;
import com.groupware.dto.minutes.AiGenerateRequest;
import com.groupware.dto.minutes.MeetingNoteResponse;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.MeetingNoteRepository;
import com.groupware.repository.MessageRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.TeamMemberRepository;
import com.groupware.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MeetingNoteAiServiceTest {

    @InjectMocks private MeetingNoteService meetingNoteService;
    @Mock private MeetingNoteRepository meetingNoteRepository;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private GeminiService geminiService;

    private User user;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        user = new User();
        ReflectionTestUtils.setField(user, "userId", "a@test.com");
        ReflectionTestUtils.setField(user, "nick", "Alice");

        room = new ChatRoom();
        ReflectionTestUtils.setField(room, "roomIdx", 1L);
        ReflectionTestUtils.setField(room, "roomName", "general");
    }

    private Message makeMessage(String nick, String content, LocalDateTime sentAt) {
        User msgUser = new User();
        ReflectionTestUtils.setField(msgUser, "userId", nick + "@test.com");
        ReflectionTestUtils.setField(msgUser, "nick", nick);

        Message m = new Message();
        ReflectionTestUtils.setField(m, "user", msgUser);
        ReflectionTestUtils.setField(m, "content", content);
        ReflectionTestUtils.setField(m, "sentAt", sentAt);
        ReflectionTestUtils.setField(m, "msgType", "TEXT");
        return m;
    }

    private Message makeFileMessage(String nick, String oriFilename, LocalDateTime sentAt) {
        Message m = makeMessage(nick, "{\"fileIdx\":5,\"oriFilename\":\"" + oriFilename + "\",\"fileSize\":100,\"fileExtension\":\"pdf\"}", sentAt);
        ReflectionTestUtils.setField(m, "msgType", "FILE");
        return m;
    }

    private AiGenerateRequest makeRequest(LocalDateTime start, LocalDateTime end) {
        AiGenerateRequest req = new AiGenerateRequest();
        ReflectionTestUtils.setField(req, "startTime", start);
        ReflectionTestUtils.setField(req, "endTime", end);
        return req;
    }

    @Test
    void AI_회의록_생성_성공() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 7, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 6, 7, 11, 0);

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);
        given(messageRepository.findMessagesForMinutes(1L, start, end)).willReturn(List.of(
                makeMessage("Alice", "안녕하세요", start.plusMinutes(1)),
                makeMessage("Bob",   "오늘 회의 시작하겠습니다", start.plusMinutes(2))
        ));
        given(geminiService.generateContent(anyString())).willReturn("## 참석자\nAlice, Bob\n\n## 결정사항\n없음");
        given(meetingNoteRepository.save(any(MeetingNote.class))).willAnswer(inv -> {
            MeetingNote n = inv.getArgument(0);
            ReflectionTestUtils.setField(n, "noteIdx", 10L);
            ReflectionTestUtils.setField(n, "createdAt", LocalDateTime.now());
            return n;
        });

        MeetingNoteResponse result = meetingNoteService.generateAiMinutes("a@test.com", 1L, makeRequest(start, end));

        assertThat(result.getNoteIdx()).isEqualTo(10L);
        assertThat(result.getTitle()).contains("AI 회의록");
        assertThat(result.getContent()).contains("참석자");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiService).generateContent(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("Alice").contains("안녕하세요");
    }

    @Test
    void AI_회의록_파일메시지_프롬프트에_파일명_포함() {
        // 항목4(일정이후): 파일 전송 메시지도 '이름 : (파일 전송) 파일명' 형식으로 프롬프트에 포함
        LocalDateTime start = LocalDateTime.of(2026, 6, 7, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 6, 7, 11, 0);

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);
        given(messageRepository.findMessagesForMinutes(1L, start, end)).willReturn(List.of(
                makeMessage("Alice", "자료 공유합니다", start.plusMinutes(1)),
                makeFileMessage("Alice", "설계서.pdf", start.plusMinutes(2))
        ));
        given(geminiService.generateContent(anyString())).willReturn("## 참석자\nAlice");
        given(meetingNoteRepository.save(any(MeetingNote.class))).willAnswer(inv -> {
            MeetingNote n = inv.getArgument(0);
            ReflectionTestUtils.setField(n, "noteIdx", 11L);
            ReflectionTestUtils.setField(n, "createdAt", LocalDateTime.now());
            return n;
        });

        meetingNoteService.generateAiMinutes("a@test.com", 1L, makeRequest(start, end));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiService).generateContent(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("(파일 전송) 설계서.pdf");
    }

    @Test
    void AI_회의록_메시지없음_예외() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 7, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 6, 7, 11, 0);

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);
        given(messageRepository.findMessagesForMinutes(anyLong(), any(), any()))
                .willReturn(Collections.emptyList());

        assertThatThrownBy(() -> meetingNoteService.generateAiMinutes("a@test.com", 1L, makeRequest(start, end)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NO_MESSAGES_IN_RANGE);
    }

    @Test
    void AI_회의록_Gemini실패_예외() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 7, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 6, 7, 11, 0);

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);
        given(messageRepository.findMessagesForMinutes(1L, start, end))
                .willReturn(List.of(makeMessage("Alice", "테스트", start.plusMinutes(1))));
        given(geminiService.generateContent(anyString()))
                .willThrow(new CustomException(ErrorCode.AI_GENERATION_FAILED));

        assertThatThrownBy(() -> meetingNoteService.generateAiMinutes("a@test.com", 1L, makeRequest(start, end)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_GENERATION_FAILED);
    }

    // ── 음성 AI 회의록 ──────────────────────────────────────────────────────

    @Test
    void 음성_AI_회의록_단일파일_성공() throws Exception {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);
        given(geminiService.generateContentFromAudio(any(), anyString())).willReturn("## 참석자\nAlice");
        given(meetingNoteRepository.save(any(MeetingNote.class))).willAnswer(inv -> {
            MeetingNote n = inv.getArgument(0);
            ReflectionTestUtils.setField(n, "noteIdx", 20L);
            ReflectionTestUtils.setField(n, "createdAt", LocalDateTime.now());
            return n;
        });

        MultipartFile file = new MockMultipartFile("audio", "rec.webm", "audio/webm", new byte[100]);
        MeetingNoteResponse result = meetingNoteService.generateVoiceMinutes("a@test.com", 1L, List.of(file));

        assertThat(result.getNoteIdx()).isEqualTo(20L);
        assertThat(result.getTitle()).contains("음성 AI 회의록");
        assertThat(result.getContent()).contains("참석자");
        verify(geminiService).generateContentFromAudio(any(), anyString());
    }

    @Test
    void 음성_AI_회의록_다중세그먼트_merge호출() throws Exception {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);
        given(geminiService.generateContentFromAudio(any(), anyString())).willReturn("## 참석자\nAlice");
        given(geminiService.mergeMinutesSections(any())).willReturn("## 참석자\nAlice, Bob\n\n## 결정사항\n없음");
        given(meetingNoteRepository.save(any(MeetingNote.class))).willAnswer(inv -> {
            MeetingNote n = inv.getArgument(0);
            ReflectionTestUtils.setField(n, "noteIdx", 21L);
            ReflectionTestUtils.setField(n, "createdAt", LocalDateTime.now());
            return n;
        });

        MultipartFile f1 = new MockMultipartFile("audio", "rec-1.webm", "audio/webm", new byte[100]);
        MultipartFile f2 = new MockMultipartFile("audio", "rec-2.webm", "audio/webm", new byte[100]);
        MeetingNoteResponse result = meetingNoteService.generateVoiceMinutes("a@test.com", 1L, List.of(f1, f2));

        assertThat(result.getNoteIdx()).isEqualTo(21L);
        verify(geminiService, times(2)).generateContentFromAudio(any(), anyString());
        verify(geminiService).mergeMinutesSections(any());
    }

    @Test
    void 음성_AI_회의록_파일없음_예외() {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);

        assertThatThrownBy(() -> meetingNoteService.generateVoiceMinutes("a@test.com", 1L, List.of()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NO_AUDIO_FILES);
    }

    @Test
    void AI_회의록_멤버아님_예외() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 7, 10, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 6, 7, 11, 0);

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(false);

        assertThatThrownBy(() -> meetingNoteService.generateAiMinutes("a@test.com", 1L, makeRequest(start, end)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_ROOM_MEMBER);
    }
}
