package com.groupware.repository;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE m.chatRoom = :room AND (m.delYn IS NULL OR m.delYn = false) ORDER BY m.msgIdx DESC")
    List<Message> findLatestMessages(@Param("room") ChatRoom room, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.chatRoom = :room AND m.msgIdx < :before AND (m.delYn IS NULL OR m.delYn = false) ORDER BY m.msgIdx DESC")
    List<Message> findMessagesBefore(@Param("room") ChatRoom room, @Param("before") Long before, Pageable pageable);

    @Query("SELECT m FROM Message m JOIN FETCH m.user WHERE m.chatRoom.roomIdx = :roomIdx AND m.sentAt BETWEEN :start AND :end AND (m.delYn IS NULL OR m.delYn = false) AND m.msgType = 'TEXT' ORDER BY m.sentAt ASC")
    List<Message> findTextMessagesByRoomAndTimeRange(@Param("roomIdx") Long roomIdx, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT m FROM Message m JOIN FETCH m.user WHERE m.chatRoom.roomIdx = :roomIdx AND (m.delYn IS NULL OR m.delYn = false) AND m.msgType = 'TEXT' AND m.content LIKE %:q% ORDER BY m.sentAt DESC")
    List<Message> searchByContent(@Param("roomIdx") Long roomIdx, @Param("q") String q, Pageable pageable);
}
