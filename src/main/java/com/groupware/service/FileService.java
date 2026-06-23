package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.Message;
import com.groupware.domain.UploadFile;
import com.groupware.domain.User;
import com.groupware.dto.chat.ChatMessagePayload;
import com.groupware.dto.file.FileResponseDto;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.MessageRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.TeamMemberRepository;
import com.groupware.repository.UploadFileRepository;
import com.groupware.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50MB
    private static final int  RECORDING_RETENTION_DAYS = 30;     // [I] 녹음 보관 기간(30일)
    public  static final String RECORDING_FILE_TYPE = "RECORDING"; // [I] 녹음 식별용 file_type 마커

    private final UploadFileRepository uploadFileRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

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
        // new_filename은 varchar(40). UUID(36)+"."+ext(최대4~)가 40 초과 가능 → 하이픈 제거(32자)로 여유 확보.
        String newFilename = UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);
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

    // [I] 음성/화상 채팅 녹음 업로드(항목20·21): 일반 파일과 달리 채팅 메시지를 만들지 않고,
    // file_type='RECORDING' + expires_at=now+30일로 저장한다. 만료 시 스케줄러가 삭제.
    @Transactional
    public FileResponseDto uploadRecording(String userId, Long roomIdx, MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new CustomException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        ChatRoom room = getActiveRoom(roomIdx);
        User user = getActiveUser(userId);
        checkAccess(room, user);

        String oriFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "recording.webm";
        String ext = extractExtension(oriFilename);
        // new_filename은 varchar(40). UUID(36)+"."+ext(최대4~)가 40 초과 가능 → 하이픈 제거(32자)로 여유 확보.
        String newFilename = UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);

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
        uploadFile.setFileType(RECORDING_FILE_TYPE);
        uploadFile.setFileExtension(ext);
        uploadFile.setFileSize(file.getSize());
        uploadFile.setExpiresAt(LocalDateTime.now().plusDays(RECORDING_RETENTION_DAYS));
        uploadFile = uploadFileRepository.save(uploadFile);

        return FileResponseDto.from(uploadFile);
    }

    @Transactional(readOnly = true)
    public List<FileResponseDto> getFiles(String userId, Long roomIdx) {
        ChatRoom room = getActiveRoom(roomIdx);
        User user = getActiveUser(userId);
        checkAccess(room, user);

        // 녹음 파일(expires_at NOT NULL)은 제외하고 일반 파일만 반환
        return uploadFileRepository.findNormalFilesByChatRoom(room)
                .stream()
                .map(FileResponseDto::from)
                .toList();
    }

    // [I] 녹음 목록(만료 전만) 반환
    @Transactional(readOnly = true)
    public List<FileResponseDto> getRecordings(String userId, Long roomIdx) {
        ChatRoom room = getActiveRoom(roomIdx);
        User user = getActiveUser(userId);
        checkAccess(room, user);

        return uploadFileRepository.findActiveRecordingsByChatRoom(room, LocalDateTime.now())
                .stream()
                .map(FileResponseDto::from)
                .toList();
    }

    // [I] 만료된 녹음 정리(스케줄러 호출용) — 물리 파일 + 레코드 삭제, 삭제 건수 반환
    @Transactional
    public int deleteExpiredRecordings() {
        List<UploadFile> expired = uploadFileRepository.findExpiredRecordings(LocalDateTime.now());
        for (UploadFile f : expired) {
            Path filePath = Paths.get(uploadDir, "files", f.getNewFilename());
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException ignored) { }
        }
        if (!expired.isEmpty()) {
            uploadFileRepository.deleteAll(expired);
        }
        return expired.size();
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

        ChatRoom room = uploadFile.getChatRoom();

        // 항목1(일정이후 추가): FILE 메시지는 남기되 '삭제된 파일'로 마킹(content에 deleted=true).
        // 파일함/메시지 어느 쪽에서 삭제해도 동일 경로 → 메시지 버블이 새로고침에도 '삭제된 파일'로 보임.
        if (room != null) {
            List<Message> fileMsgs = messageRepository.findFileMessagesByRoomAndFileIdx(
                    room, "%\"fileIdx\":" + fileIdx + ",%");
            for (Message m : fileMsgs) {
                try {
                    ObjectNode node = (ObjectNode) objectMapper.readTree(m.getContent());
                    node.put("deleted", true);
                    m.setContent(objectMapper.writeValueAsString(node));
                    messageRepository.save(m);
                } catch (Exception ignored) { }
            }
        }

        // 실제 파일 삭제(레코드 + 물리 파일)
        Path filePath = Paths.get(uploadDir, "files", uploadFile.getNewFilename());
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) { }
        uploadFileRepository.delete(uploadFile);

        // 실시간 양방향 반영(메시지 버블 + 파일함): FILE_DELETED 브로드캐스트
        if (room != null) {
            messagingTemplate.convertAndSend("/topic/room/" + room.getRoomIdx(),
                    ChatMessagePayload.builder()
                            .roomIdx(room.getRoomIdx())
                            .msgType("FILE_DELETED")
                            .content(String.valueOf(fileIdx))
                            .build());
        }
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
