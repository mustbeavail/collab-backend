package com.groupware.repository;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.UploadFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UploadFileRepository extends JpaRepository<UploadFile, Long> {

    @Query("SELECT f FROM UploadFile f WHERE f.chatRoom = :room ORDER BY f.createdAt DESC")
    List<UploadFile> findByChatRoomOrderByCreatedAtDesc(@Param("room") ChatRoom room);

    @Query("SELECT f FROM UploadFile f JOIN FETCH f.user WHERE f.chatRoom.roomIdx = :roomIdx AND f.oriFilename LIKE %:q% ORDER BY f.createdAt DESC")
    List<UploadFile> searchByFilename(@Param("roomIdx") Long roomIdx, @Param("q") String q);
}
