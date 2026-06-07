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

    @Query("SELECT s FROM Schedule s JOIN FETCH s.user WHERE s.delAt IS NULL AND s.chatRoom IS NOT NULL " +
           "AND EXISTS (SELECT rm FROM RoomMember rm WHERE rm.chatRoom = s.chatRoom AND rm.user.userId = :userId AND rm.exitAt IS NULL) " +
           "ORDER BY s.appointmentDate ASC")
    List<Schedule> findMySchedules(@Param("userId") String userId);

    @Query("SELECT s FROM Schedule s JOIN FETCH s.user WHERE s.chatRoom.roomIdx = :roomIdx AND s.delAt IS NULL " +
           "AND s.title LIKE %:q% ORDER BY s.appointmentDate ASC")
    List<Schedule> searchByTitle(@Param("roomIdx") Long roomIdx, @Param("q") String q);
}
