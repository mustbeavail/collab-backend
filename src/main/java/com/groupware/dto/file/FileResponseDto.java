package com.groupware.dto.file;

import com.groupware.domain.UploadFile;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class FileResponseDto {

    private Long fileIdx;
    private String oriFilename;
    private String fileExtension;
    private Long fileSize;
    private LocalDateTime createdAt;
    private String uploaderNickname;
    private String uploaderId;
    private LocalDateTime expiresAt; // [I] 녹음 파일 만료일시(일반 파일은 null)

    public static FileResponseDto from(UploadFile f) {
        return FileResponseDto.builder()
                .fileIdx(f.getFileIdx())
                .oriFilename(f.getOriFilename())
                .fileExtension(f.getFileExtension())
                .fileSize(f.getFileSize())
                .createdAt(f.getCreatedAt())
                .uploaderNickname(f.getUser().getNick())
                .uploaderId(f.getUser().getUserId())
                .expiresAt(f.getExpiresAt())
                .build();
    }
}
