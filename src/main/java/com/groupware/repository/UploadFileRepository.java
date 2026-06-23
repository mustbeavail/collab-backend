package com.groupware.repository;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.UploadFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UploadFileRepository extends JpaRepository<UploadFile, Long> {

    @Query("SELECT f FROM UploadFile f WHERE f.chatRoom = :room ORDER BY f.createdAt DESC")
    List<UploadFile> findByChatRoomOrderByCreatedAtDesc(@Param("room") ChatRoom room);

    // [I] 일반 파일 목록(녹음 제외) — expires_at IS NULL
    @Query("SELECT f FROM UploadFile f WHERE f.chatRoom = :room AND f.expiresAt IS NULL ORDER BY f.createdAt DESC")
    List<UploadFile> findNormalFilesByChatRoom(@Param("room") ChatRoom room);

    // [I] 녹음 목록(만료 전만) — expires_at IS NOT NULL AND expires_at > now
    @Query("SELECT f FROM UploadFile f WHERE f.chatRoom = :room AND f.expiresAt IS NOT NULL AND f.expiresAt > :now ORDER BY f.createdAt DESC")
    List<UploadFile> findActiveRecordingsByChatRoom(@Param("room") ChatRoom room, @Param("now") LocalDateTime now);

    // [I] 만료된 녹음(스케줄러 정리용) — expires_at IS NOT NULL AND expires_at < now
    @Query("SELECT f FROM UploadFile f WHERE f.expiresAt IS NOT NULL AND f.expiresAt < :now")
    List<UploadFile> findExpiredRecordings(@Param("now") LocalDateTime now);

    @Query("SELECT f FROM UploadFile f JOIN FETCH f.user WHERE f.chatRoom.roomIdx = :roomIdx AND f.oriFilename LIKE %:q% ORDER BY f.createdAt DESC")
    List<UploadFile> searchByFilename(@Param("roomIdx") Long roomIdx, @Param("q") String q);
}
