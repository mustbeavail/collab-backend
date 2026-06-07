package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.MeetingNote;
import com.groupware.domain.User;
import com.groupware.dto.minutes.CreateMeetingNoteRequest;
import com.groupware.dto.minutes.MeetingNoteResponse;
import com.groupware.dto.minutes.UpdateMeetingNoteRequest;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.MeetingNoteRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.TeamMemberRepository;
import com.groupware.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MeetingNoteServiceTest {

    @InjectMocks private MeetingNoteService meetingNoteService;
    @Mock private MeetingNoteRepository meetingNoteRepository;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private UserRepository userRepository;

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

    private MeetingNote makeNote(Long idx, String title, String content) {
        MeetingNote n = new MeetingNote();
        ReflectionTestUtils.setField(n, "noteIdx", idx);
        ReflectionTestUtils.setField(n, "user", user);
        ReflectionTestUtils.setField(n, "chatRoom", room);
        ReflectionTestUtils.setField(n, "title", title);
        ReflectionTestUtils.setField(n, "content", content);
        ReflectionTestUtils.setField(n, "createdAt", LocalDateTime.of(2026, 6, 7, 10, 0));
        return n;
    }

    // ── 목록 조회 ────────────────────────────────────────────

    @Test
    void 회의록_목록_조회_성공() {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);
        given(meetingNoteRepository.findByChatRoomRoomIdxAndDelAtIsNullOrderByCreatedAtDesc(1L))
                .willReturn(List.of(makeNote(2L, "2차 회의", null), makeNote(1L, "1차 회의", "내용")));

        List<MeetingNoteResponse> result = meetingNoteService.getNotesByRoom("a@test.com", 1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("2차 회의");
    }

    @Test
    void 회의록_목록_조회_채팅방없음_예외() {
        given(chatRoomRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> meetingNoteService.getNotesByRoom("a@test.com", 99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void 회의록_목록_조회_멤버아님_예외() {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(false);

        assertThatThrownBy(() -> meetingNoteService.getNotesByRoom("a@test.com", 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_ROOM_MEMBER);
    }

    // ── 생성 ────────────────────────────────────────────────

    @Test
    void 회의록_생성_성공() {
        CreateMeetingNoteRequest req = new CreateMeetingNoteRequest();
        ReflectionTestUtils.setField(req, "title", "스프린트 회의");
        ReflectionTestUtils.setField(req, "content", "## 안건\n목표 논의");

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);
        given(meetingNoteRepository.save(any(MeetingNote.class))).willAnswer(inv -> {
            MeetingNote n = inv.getArgument(0);
            ReflectionTestUtils.setField(n, "noteIdx", 1L);
            ReflectionTestUtils.setField(n, "createdAt", LocalDateTime.of(2026, 6, 7, 10, 0));
            return n;
        });

        MeetingNoteResponse result = meetingNoteService.createNote("a@test.com", 1L, req);

        assertThat(result.getNoteIdx()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("스프린트 회의");
        assertThat(result.getContent()).isEqualTo("## 안건\n목표 논의");
        assertThat(result.getAuthorNick()).isEqualTo("Alice");
    }

    @Test
    void 회의록_생성_멤버아님_예외() {
        CreateMeetingNoteRequest req = new CreateMeetingNoteRequest();
        ReflectionTestUtils.setField(req, "title", "제목");

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(false);

        assertThatThrownBy(() -> meetingNoteService.createNote("a@test.com", 1L, req))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_ROOM_MEMBER);
    }

    // ── 수정 ────────────────────────────────────────────────

    @Test
    void 회의록_수정_성공() {
        MeetingNote existing = makeNote(1L, "기존 제목", "기존 내용");
        UpdateMeetingNoteRequest req = new UpdateMeetingNoteRequest();
        ReflectionTestUtils.setField(req, "title", "수정된 제목");
        ReflectionTestUtils.setField(req, "content", "수정된 내용");

        given(meetingNoteRepository.findByNoteIdxAndDelAtIsNull(1L)).willReturn(Optional.of(existing));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));

        MeetingNoteResponse result = meetingNoteService.updateNote("a@test.com", 1L, req);

        assertThat(result.getTitle()).isEqualTo("수정된 제목");
        assertThat(result.getContent()).isEqualTo("수정된 내용");
    }

    @Test
    void 회의록_수정_작성자아님_예외() {
        User other = new User();
        ReflectionTestUtils.setField(other, "userId", "b@test.com");
        ReflectionTestUtils.setField(other, "nick", "Bob");

        MeetingNote noteByOther = makeNote(1L, "남의 회의록", "내용");
        ReflectionTestUtils.setField(noteByOther, "user", other);

        UpdateMeetingNoteRequest req = new UpdateMeetingNoteRequest();
        ReflectionTestUtils.setField(req, "title", "수정 시도");

        given(meetingNoteRepository.findByNoteIdxAndDelAtIsNull(1L)).willReturn(Optional.of(noteByOther));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> meetingNoteService.updateNote("a@test.com", 1L, req))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEETING_NOTE_ACCESS_DENIED);
    }

    @Test
    void 회의록_수정_없는회의록_예외() {
        given(meetingNoteRepository.findByNoteIdxAndDelAtIsNull(99L)).willReturn(Optional.empty());

        UpdateMeetingNoteRequest req = new UpdateMeetingNoteRequest();
        ReflectionTestUtils.setField(req, "title", "제목");

        assertThatThrownBy(() -> meetingNoteService.updateNote("a@test.com", 99L, req))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEETING_NOTE_NOT_FOUND);
    }

    // ── 삭제 ────────────────────────────────────────────────

    @Test
    void 회의록_삭제_성공() {
        MeetingNote existing = makeNote(1L, "삭제할 회의록", null);

        given(meetingNoteRepository.findByNoteIdxAndDelAtIsNull(1L)).willReturn(Optional.of(existing));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);

        meetingNoteService.deleteNote("a@test.com", 1L);

        assertThat(existing.getDelAt()).isNotNull();
    }

    @Test
    void 회의록_삭제_없는회의록_예외() {
        given(meetingNoteRepository.findByNoteIdxAndDelAtIsNull(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> meetingNoteService.deleteNote("a@test.com", 99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEETING_NOTE_NOT_FOUND);
    }
}
