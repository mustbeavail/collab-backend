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

    // 항목4(일정이후 추가): AI 회의록 프롬프트에 TEXT + FILE 메시지 모두 포함(파일 메시지는 '이름 : 파일명'으로 정리)
    @Query("SELECT m FROM Message m JOIN FETCH m.user WHERE m.chatRoom.roomIdx = :roomIdx AND m.sentAt BETWEEN :start AND :end AND (m.delYn IS NULL OR m.delYn = false) AND m.msgType IN ('TEXT', 'FILE') ORDER BY m.sentAt ASC")
    List<Message> findMessagesForMinutes(@Param("roomIdx") Long roomIdx, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 항목2(일정이후 추가): AI 회의록 시간범위 디폴트 — 방의 가장 오래된/최신 메시지 시각(삭제 제외, TEXT+FILE)
    @Query("SELECT MIN(m.sentAt) FROM Message m WHERE m.chatRoom.roomIdx = :roomIdx AND (m.delYn IS NULL OR m.delYn = false) AND m.msgType IN ('TEXT', 'FILE')")
    LocalDateTime findEarliestSentAt(@Param("roomIdx") Long roomIdx);

    @Query("SELECT MAX(m.sentAt) FROM Message m WHERE m.chatRoom.roomIdx = :roomIdx AND (m.delYn IS NULL OR m.delYn = false) AND m.msgType IN ('TEXT', 'FILE')")
    LocalDateTime findLatestSentAt(@Param("roomIdx") Long roomIdx);

    @Query("SELECT m FROM Message m JOIN FETCH m.user WHERE m.chatRoom.roomIdx = :roomIdx AND (m.delYn IS NULL OR m.delYn = false) AND m.msgType = 'TEXT' AND m.content LIKE %:q% ORDER BY m.sentAt DESC")
    List<Message> searchByContent(@Param("roomIdx") Long roomIdx, @Param("q") String q, Pageable pageable);

    // 항목1(일정이후): 특정 파일(fileIdx)을 참조하는 FILE 메시지 — 삭제 시 '삭제된 파일' 마킹용
    @Query("SELECT m FROM Message m WHERE m.chatRoom = :room AND m.msgType = 'FILE' AND (m.delYn IS NULL OR m.delYn = false) AND m.content LIKE :pat")
    List<Message> findFileMessagesByRoomAndFileIdx(@Param("room") ChatRoom room, @Param("pat") String pat);
}
