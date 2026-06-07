package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.UploadFile;
import com.groupware.domain.User;
import com.groupware.dto.file.FileResponseDto;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.TeamMemberRepository;
import com.groupware.repository.UploadFileRepository;
import com.groupware.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50MB

    private final UploadFileRepository uploadFileRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Transactional
    public FileResponseDto upload(String userId, Long roomIdx, MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new CustomException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        ChatRoom room = getActiveRoom(roomIdx);
        User user = getActiveUser(userId);
        checkAccess(room, user);

        String oriFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        String ext = extractExtension(oriFilename);
        String newFilename = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        String mimeType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        Path filesDir = Paths.get(uploadDir, "files");
        try {
            Files.createDirectories(filesDir);
            Files.copy(file.getInputStream(), filesDir.resolve(newFilename));
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        UploadFile uploadFile = new UploadFile();
        uploadFile.setUser(user);
        uploadFile.setChatRoom(room);
        uploadFile.setOriFilename(oriFilename);
        uploadFile.setNewFilename(newFilename);
        uploadFile.setFilePath("/files/" + newFilename);
        uploadFile.setFileType(mimeType);
        uploadFile.setFileExtension(ext);
        uploadFile.setFileSize(file.getSize());
        uploadFile = uploadFileRepository.save(uploadFile);

        return FileResponseDto.from(uploadFile);
    }

    @Transactional(readOnly = true)
    public List<FileResponseDto> getFiles(String userId, Long roomIdx) {
        ChatRoom room = getActiveRoom(roomIdx);
        User user = getActiveUser(userId);
        checkAccess(room, user);

        return uploadFileRepository.findByChatRoomOrderByCreatedAtDesc(room)
                .stream()
                .map(FileResponseDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Resource download(String userId, Long fileIdx) {
        UploadFile uploadFile = uploadFileRepository.findById(fileIdx)
                .orElseThrow(() -> new CustomException(ErrorCode.FILE_NOT_FOUND));

        User user = getActiveUser(userId);
        checkAccess(uploadFile.getChatRoom(), user);

        Path filePath = Paths.get(uploadDir, "files", uploadFile.getNewFilename());
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) throw new CustomException(ErrorCode.FILE_NOT_FOUND);
            return resource;
        } catch (MalformedURLException e) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }
    }

    @Transactional
    public void delete(String userId, Long fileIdx) {
        UploadFile uploadFile = uploadFileRepository.findById(fileIdx)
                .orElseThrow(() -> new CustomException(ErrorCode.FILE_NOT_FOUND));

        if (!uploadFile.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FILE_ACCESS_DENIED);
        }

        Path filePath = Paths.get(uploadDir, "files", uploadFile.getNewFilename());
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) { }

        uploadFileRepository.delete(uploadFile);
    }

    private ChatRoom getActiveRoom(Long roomIdx) {
        return chatRoomRepository.findById(roomIdx)
                .filter(r -> r.getDelDate() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    private User getActiveUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private void checkAccess(ChatRoom room, User user) {
        if (room.getTeam() != null) {
            teamMemberRepository.findActiveByTeamIdxAndUserId(room.getTeam().getTeamIdx(), user.getUserId())
                    .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAM_MEMBER));
        } else {
            if (!roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)) {
                throw new CustomException(ErrorCode.NOT_ROOM_MEMBER);
            }
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
