package com.groupware.repository;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE m.chatRoom = :room AND (m.delYn IS NULL OR m.delYn = false) ORDER BY m.msgIdx DESC")
    List<Message> findLatestMessages(@Param("room") ChatRoom room, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.chatRoom = :room AND m.msgIdx < :before AND (m.delYn IS NULL OR m.delYn = false) ORDER BY m.msgIdx DESC")
    List<Message> findMessagesBefore(@Param("room") ChatRoom room, @Param("before") Long before, Pageable pageable);
}
