package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.Schedule;
import com.groupware.domain.User;
import com.groupware.dto.schedule.CreateScheduleRequest;
import com.groupware.dto.schedule.ScheduleResponse;
import com.groupware.dto.schedule.UpdateScheduleRequest;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.ScheduleRepository;
import com.groupware.repository.TeamMemberRepository;
import com.groupware.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @InjectMocks private ScheduleService scheduleService;
    @Mock private ScheduleRepository scheduleRepository;
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

    private Schedule makeSchedule(Long idx, String title, LocalDateTime date) {
        Schedule s = new Schedule();
        ReflectionTestUtils.setField(s, "scheduleIdx", idx);
        ReflectionTestUtils.setField(s, "user", user);
        ReflectionTestUtils.setField(s, "chatRoom", room);
        ReflectionTestUtils.setField(s, "title", title);
        ReflectionTestUtils.setField(s, "appointmentDate", date);
        return s;
    }

    @Test
    void 일정_목록_조회_성공() {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);
        given(scheduleRepository.findByChatRoomRoomIdxAndDelAtIsNullOrderByAppointmentDateAsc(1L))
                .willReturn(List.of(
                        makeSchedule(1L, "회의", LocalDateTime.of(2026, 6, 10, 10, 0)),
                        makeSchedule(2L, "점심", LocalDateTime.of(2026, 6, 15, 14, 0))
                ));

        List<ScheduleResponse> result = scheduleService.getSchedulesByRoom("a@test.com", 1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("회의");
        assertThat(result.get(1).getTitle()).isEqualTo("점심");
    }

    @Test
    void 일정_목록_조회_방없음_예외() {
        given(chatRoomRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.getSchedulesByRoom("a@test.com", 99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void 일정_목록_조회_멤버아님_예외() {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(false);

        assertThatThrownBy(() -> scheduleService.getSchedulesByRoom("a@test.com", 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_ROOM_MEMBER);
    }

    @Test
    void 일정_생성_성공() {
        CreateScheduleRequest req = new CreateScheduleRequest();
        ReflectionTestUtils.setField(req, "title", "스프린트 회의");
        ReflectionTestUtils.setField(req, "date", LocalDateTime.of(2026, 7, 1, 9, 0));
        ReflectionTestUtils.setField(req, "participants", List.of("Alice", "Bob"));
        ReflectionTestUtils.setField(req, "content", "목표 논의");
        ReflectionTestUtils.setField(req, "location", "회의실 A");

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "scheduleIdx", 1L);
            return s;
        });

        ScheduleResponse result = scheduleService.createSchedule("a@test.com", 1L, req);

        assertThat(result.getScheduleIdx()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("스프린트 회의");
        assertThat(result.getParticipants()).containsExactly("Alice", "Bob");
        assertThat(result.getDate()).isEqualTo("2026-07-01T09:00");

        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository).save(captor.capture());
        assertThat(captor.getValue().getParticipants()).isEqualTo("Alice,Bob");
    }

    @Test
    void 일정_생성_멤버아님_예외() {
        CreateScheduleRequest req = new CreateScheduleRequest();
        ReflectionTestUtils.setField(req, "title", "테스트");
        ReflectionTestUtils.setField(req, "date", LocalDateTime.now());

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(false);

        assertThatThrownBy(() -> scheduleService.createSchedule("a@test.com", 1L, req))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_ROOM_MEMBER);
    }

    @Test
    void 일정_수정_성공() {
        Schedule existing = makeSchedule(1L, "기존 제목", LocalDateTime.of(2026, 6, 1, 0, 0));
        UpdateScheduleRequest req = new UpdateScheduleRequest();
        ReflectionTestUtils.setField(req, "title", "수정된 제목");
        ReflectionTestUtils.setField(req, "date", LocalDateTime.of(2026, 6, 20, 10, 0));
        ReflectionTestUtils.setField(req, "participants", List.of("Charlie"));
        ReflectionTestUtils.setField(req, "content", "변경된 내용");
        ReflectionTestUtils.setField(req, "location", "");

        given(scheduleRepository.findByScheduleIdxAndDelAtIsNull(1L)).willReturn(Optional.of(existing));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);

        ScheduleResponse result = scheduleService.updateSchedule("a@test.com", 1L, req);

        assertThat(result.getTitle()).isEqualTo("수정된 제목");
        assertThat(result.getDate()).isEqualTo("2026-06-20T10:00");
        assertThat(result.getParticipants()).containsExactly("Charlie");
    }

    @Test
    void 일정_수정_없는일정_예외() {
        given(scheduleRepository.findByScheduleIdxAndDelAtIsNull(99L)).willReturn(Optional.empty());

        UpdateScheduleRequest req = new UpdateScheduleRequest();
        ReflectionTestUtils.setField(req, "title", "제목");
        ReflectionTestUtils.setField(req, "date", LocalDateTime.now());

        assertThatThrownBy(() -> scheduleService.updateSchedule("a@test.com", 99L, req))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    void 일정_삭제_성공() {
        Schedule existing = makeSchedule(1L, "삭제할 일정", LocalDateTime.now());

        given(scheduleRepository.findByScheduleIdxAndDelAtIsNull(1L)).willReturn(Optional.of(existing));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);

        scheduleService.deleteSchedule("a@test.com", 1L);

        assertThat(existing.getDelAt()).isNotNull();
    }

    @Test
    void 일정_삭제_없는일정_예외() {
        given(scheduleRepository.findByScheduleIdxAndDelAtIsNull(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.deleteSchedule("a@test.com", 99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    void 내_전체_일정_조회_성공() {
        given(scheduleRepository.findMySchedules("a@test.com")).willReturn(List.of(
                makeSchedule(1L, "팀 회의", LocalDateTime.of(2026, 7, 1, 10, 0)),
                makeSchedule(2L, "고객 미팅", LocalDateTime.of(2026, 7, 5, 14, 0))
        ));

        List<ScheduleResponse> result = scheduleService.getMySchedules("a@test.com");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("팀 회의");
        assertThat(result.get(1).getTitle()).isEqualTo("고객 미팅");
    }

    @Test
    void 내_전체_일정_조회_빈_목록() {
        given(scheduleRepository.findMySchedules("a@test.com")).willReturn(List.of());

        List<ScheduleResponse> result = scheduleService.getMySchedules("a@test.com");

        assertThat(result).isEmpty();
    }
}
