package com.groupware.repository;

import com.groupware.domain.MeetingNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MeetingNoteRepository extends JpaRepository<MeetingNote, Long> {

    List<MeetingNote> findByChatRoomRoomIdxAndDelAtIsNullOrderByCreatedAtDesc(Long roomIdx);

    Optional<MeetingNote> findByNoteIdxAndDelAtIsNull(Long noteIdx);

    @Query("SELECT n FROM MeetingNote n JOIN FETCH n.user WHERE n.chatRoom.roomIdx = :roomIdx AND n.delAt IS NULL " +
           "AND (n.title LIKE %:q% OR n.content LIKE %:q%) ORDER BY n.createdAt DESC")
    List<MeetingNote> searchByRoomAndKeyword(@Param("roomIdx") Long roomIdx, @Param("q") String q);
}
