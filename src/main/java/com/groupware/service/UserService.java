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

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getWithdrwalAt() != null) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UserProfileResponse updateMyProfile(String userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getWithdrwalAt() != null) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        user.setNick(request.getNickname());
        user.setAbout(request.getAbout());
        userRepository.save(user);
        return UserProfileResponse.from(user);
    }

    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getWithdrwalAt() != null) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPw())) {
            throw new CustomException(ErrorCode.WRONG_CURRENT_PASSWORD);
        }
        user.setPw(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void withdraw(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getWithdrwalAt() != null) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        user.setWithdrwalAt(LocalDateTime.now());
        userRepository.save(user);
    }

    @Transactional
    public String uploadAvatar(String userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getWithdrwalAt() != null) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }

        String ext = extractExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);

        Path avatarDir = Paths.get(uploadDir, "avatars");
        try {
            Files.createDirectories(avatarDir);
            Files.copy(file.getInputStream(), avatarDir.resolve(filename));
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
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
        if (user.getWithdrwalAt() != null) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        return UserProfileResponse.from(user);
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
    }
}
