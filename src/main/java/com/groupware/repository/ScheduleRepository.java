package com.groupware.repository;

import com.groupware.domain.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByChatRoomRoomIdxAndDelAtIsNullOrderByAppointmentDateAsc(Long roomIdx);

    Optional<Schedule> findByScheduleIdxAndDelAtIsNull(Long scheduleIdx);

    // 내가 속한 일정 전부(qa 항목22): 비팀 방은 RoomMember, 팀 채널은 팀 멤버면 전부(채널 미참여여도 포함)
    @Query("SELECT s FROM Schedule s JOIN FETCH s.user WHERE s.delAt IS NULL AND s.chatRoom IS NOT NULL AND (" +
           "  (s.chatRoom.team IS NULL AND EXISTS (SELECT rm FROM RoomMember rm WHERE rm.chatRoom = s.chatRoom AND rm.user.userId = :userId AND rm.exitAt IS NULL))" +
           "  OR (s.chatRoom.team IS NOT NULL AND EXISTS (SELECT tm FROM TeamMember tm WHERE tm.team = s.chatRoom.team AND tm.user.userId = :userId AND tm.exitAt IS NULL AND (tm.status IS NULL OR tm.status = 'ACTIVE')))" +
           ") ORDER BY s.appointmentDate ASC")
    List<Schedule> findMySchedules(@Param("userId") String userId);

    @Query("SELECT s FROM Schedule s JOIN FETCH s.user WHERE s.chatRoom.roomIdx = :roomIdx AND s.delAt IS NULL " +
           "AND s.title LIKE %:q% ORDER BY s.appointmentDate ASC")
    List<Schedule> searchByTitle(@Param("roomIdx") Long roomIdx, @Param("q") String q);
}
