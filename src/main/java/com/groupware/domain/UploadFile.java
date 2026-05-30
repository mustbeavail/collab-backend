package com.groupware.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "file_type", length = 10)
    private String fileType;

    @Column(name = "file_extension", length = 10)
    private String fileExtension;
}
