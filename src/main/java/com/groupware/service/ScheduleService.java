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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ScheduleResponse> getMySchedules(String userId) {
        return scheduleRepository.findMySchedules(userId)
                .stream().map(ScheduleResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesByRoom(String userId, Long roomIdx) {
        ChatRoom room = getActiveRoom(roomIdx);
        User user = getActiveUser(userId);
        checkAccess(room, user);
        return scheduleRepository
                .findByChatRoomRoomIdxAndDelAtIsNullOrderByAppointmentDateAsc(roomIdx)
                .stream().map(ScheduleResponse::from).collect(Collectors.toList());
    }

    @Transactional
    public ScheduleResponse createSchedule(String userId, Long roomIdx, CreateScheduleRequest req) {
        ChatRoom room = getActiveRoom(roomIdx);
        User user = getActiveUser(userId);
        checkAccess(room, user);

        Schedule schedule = new Schedule();
        schedule.setUser(user);
        schedule.setChatRoom(room);
        schedule.setTitle(req.getTitle());
        schedule.setAppointmentDate(req.getDate());
        schedule.setContent(req.getContent());
        schedule.setLocation(req.getLocation());
        schedule.setLat(req.getLat());
        schedule.setLongt(req.getLng());
        if (req.getParticipants() != null && !req.getParticipants().isEmpty()) {
            schedule.setParticipants(String.join(",", req.getParticipants()));
        }
        return ScheduleResponse.from(scheduleRepository.save(schedule));
    }

    @Transactional
    public ScheduleResponse updateSchedule(String userId, Long scheduleIdx, UpdateScheduleRequest req) {
        Schedule schedule = scheduleRepository.findByScheduleIdxAndDelAtIsNull(scheduleIdx)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_NOT_FOUND));
        User user = getActiveUser(userId);
        if (schedule.getChatRoom() != null) {
            checkAccess(schedule.getChatRoom(), user);
        }

        schedule.setTitle(req.getTitle());
        schedule.setAppointmentDate(req.getDate());
        schedule.setContent(req.getContent());
        schedule.setLocation(req.getLocation());
        schedule.setLat(req.getLat());
        schedule.setLongt(req.getLng());
        schedule.setParticipants(
                (req.getParticipants() != null && !req.getParticipants().isEmpty())
                        ? String.join(",", req.getParticipants())
                        : null
        );
        return ScheduleResponse.from(schedule);
    }

    @Transactional
    public void deleteSchedule(String userId, Long scheduleIdx) {
        Schedule schedule = scheduleRepository.findByScheduleIdxAndDelAtIsNull(scheduleIdx)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_NOT_FOUND));
        User user = getActiveUser(userId);
        if (schedule.getChatRoom() != null) {
            checkAccess(schedule.getChatRoom(), user);
        }
        schedule.setDelAt(LocalDateTime.now());
    }

    private ChatRoom getActiveRoom(Long roomIdx) {
        return chatRoomRepository.findById(roomIdx)
                .filter(r -> r.getDelDate() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    private User getActiveUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private void checkAccess(ChatRoom room, User user) {
        if (room.getTeam() != null) {
            teamMemberRepository.findActiveByTeamIdxAndUserId(
                    room.getTeam().getTeamIdx(), user.getUserId())
                    .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAM_MEMBER));
        } else {
            if (!roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)) {
                throw new CustomException(ErrorCode.NOT_ROOM_MEMBER);
            }
        }
    }
}
