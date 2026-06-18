package com.groupware.service;

import com.groupware.domain.User;
import com.groupware.dto.user.ChangePasswordRequest;
import com.groupware.dto.user.UpdateProfileRequest;
import com.groupware.dto.user.UserProfileResponse;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TeamService teamService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getWithdrawalAt() != null) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UserProfileResponse updateMyProfile(String userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getWithdrawalAt() != null) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        // 닉네임 중복 검증(본인 제외 활성 유저). DB UNIQUE(nick) 위반 전에 차단.
        if (userRepository.existsNickByOtherUser(request.getNickname(), userId)) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        user.setNick(request.getNickname());
        // null이면 기존 about 유지, 빈 문자열("")은 의도적 삭제로 허용
        if (request.getAbout() != null) {
            user.setAbout(request.getAbout());
        }
        userRepository.save(user);
        return UserProfileResponse.from(user);
    }

    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getWithdrawalAt() != null) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPw())) {
            throw new CustomException(ErrorCode.WRONG_CURRENT_PASSWORD);
        }
        // 새 비밀번호가 현재 비밀번호와 동일하면 변경 차단
        if (passwordEncoder.matches(request.getNewPassword(), user.getPw())) {
            throw new CustomException(ErrorCode.SAME_AS_CURRENT_PASSWORD);
        }
        user.setPw(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void withdraw(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getWithdrawalAt() != null) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        // 탈퇴 전, 리더로 있는 팀의 리더권 자동 위임(추가2)
        teamService.reassignLeadershipForWithdrawnUser(userId);
        user.setWithdrawalAt(LocalDateTime.now());
        // 닉네임 해제 → 탈퇴 회원의 닉네임을 다른 사용자가 재사용 가능
        // (DB UNIQUE(nick)는 NULL 다중 허용이므로 탈퇴 회원끼리 충돌 없음)
        user.setNick(null);
        userRepository.save(user);
    }

    private static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024; // 5MB
    private static final java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Set.of("png", "jpg", "jpeg", "webp");
    private static final java.util.Map<String, byte[]> MAGIC_BYTES = java.util.Map.of(
            "png",  new byte[]{(byte)0x89, 0x50, 0x4E, 0x47},
            "jpg",  new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF},
            "jpeg", new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF},
            "webp", new byte[]{0x52, 0x49, 0x46, 0x46}
    );

    @Transactional
    public String uploadAvatar(String userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getWithdrawalAt() != null) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }

        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }

        String ext = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }

        try {
            byte[] header = file.getInputStream().readNBytes(4);
            byte[] magic = MAGIC_BYTES.get(ext);
            if (magic != null) {
                for (int i = 0; i < magic.length; i++) {
                    if (header[i] != magic[i]) throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
                }
            }
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }

        String filename = UUID.randomUUID() + "." + ext;
        Path avatarDir = Paths.get(uploadDir, "avatars");
        try {
            Files.createDirectories(avatarDir);
            Files.copy(file.getInputStream(), avatarDir.resolve(filename));
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        // 기존 아바타 파일 삭제
        if (user.getAvatarUrl() != null && user.getAvatarUrl().startsWith("/avatars/")) {
            String oldFilename = user.getAvatarUrl().substring("/avatars/".length());
            try {
                Files.deleteIfExists(Paths.get(uploadDir, "avatars", oldFilename));
            } catch (IOException ignored) { }
        }

        String avatarUrl = "/avatars/" + filename;
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
        return avatarUrl;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUserPublicProfile(String targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getWithdrawalAt() != null) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        return UserProfileResponse.from(user);
    }

    @Transactional(readOnly = true)
    public boolean isNicknameAvailable(String currentUserId, String nickname) {
        return !userRepository.existsNickByOtherUser(nickname, currentUserId);
    }

    @Transactional
    public void deleteAvatar(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getWithdrawalAt() != null) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        String url = user.getAvatarUrl();
        if (url != null && url.startsWith("/avatars/")) {
            String filename = url.substring("/avatars/".length());
            try {
                Files.deleteIfExists(Paths.get(uploadDir, "avatars", filename));
            } catch (IOException ignored) { }
        }
        user.setAvatarUrl(null);
        userRepository.save(user);
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
    }
}
