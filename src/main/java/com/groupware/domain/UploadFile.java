package com.groupware.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "files")
@Getter @Setter @NoArgsConstructor
public class UploadFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_idx", nullable = false)
    private Long fileIdx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom chatRoom;

    @Column(name = "new_filename", length = 40, nullable = false)
    private String newFilename;

    @Column(name = "ori_filename", length = 100)
    private String oriFilename;

    @Column(name = "file_path", length = 255)
    private String filePath;

    // MIME 타입 보관(예: xlsx 65자) — varchar(10)은 짧아 100으로 확장(*추가3)
    @Column(name = "file_type", length = 100)
    private String fileType;

    @Column(name = "file_extension", length = 10)
    private String fileExtension;

    @Column(name = "file_size")
    private Long fileSize;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // [I] 녹음 파일 만료일시. null = 일반 파일(영구 보관). not null = 녹음 파일(만료 시 스케줄러가 삭제).
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
