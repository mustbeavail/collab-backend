package com.groupware.service;

import com.groupware.domain.User;
import com.groupware.dto.user.ChangePasswordRequest;
import com.groupware.dto.user.UpdateProfileRequest;
import com.groupware.dto.user.UserProfileResponse;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks private UserService userService;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TeamService teamService;

    @Test
    void 내_프로필_조회_성공() {
        User user = buildUser("uid-1", "test@test.com", "테스터", "안녕하세요", null);
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));

        UserProfileResponse result = userService.getMyProfile("uid-1");

        assertThat(result.getUserId()).isEqualTo("uid-1");
        assertThat(result.getEmail()).isEqualTo("uid-1");
        assertThat(result.getNickname()).isEqualTo("테스터");
        assertThat(result.getAbout()).isEqualTo("안녕하세요");
    }

    @Test
    void 내_프로필_조회_소개없음_성공() {
        User user = buildUser("uid-1", "test@test.com", "테스터", null, null);
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));

        UserProfileResponse result = userService.getMyProfile("uid-1");

        assertThat(result.getAbout()).isNull();
    }

    @Test
    void 내_프로필_조회_사용자없음_예외() {
        given(userRepository.findById("uid-x")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyProfile("uid-x"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void 내_프로필_조회_탈퇴한_사용자_예외() {
        User user = buildUser("uid-1", "test@test.com", "테스터", null, LocalDateTime.now());
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.getMyProfile("uid-1"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.WITHDRAWN_USER));
    }

    // ─── updateMyProfile ──────────────────────────────────────────────────

    @Test
    void 프로필_수정_성공() {
        User user = buildUser("uid-1", "test@test.com", "기존닉네임", "기존소개", null);
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);

        UpdateProfileRequest request = buildRequest("새닉네임", "새소개");
        UserProfileResponse result = userService.updateMyProfile("uid-1", request);

        assertThat(result.getNickname()).isEqualTo("새닉네임");
        assertThat(result.getAbout()).isEqualTo("새소개");
        verify(userRepository).save(user);
    }

    @Test
    void 프로필_수정_닉네임_중복_예외() {
        User user = buildUser("uid-1", "test@test.com", "기존닉네임", "기존소개", null);
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));
        given(userRepository.existsNickByOtherUser("중복닉", "uid-1")).willReturn(true);

        assertThatThrownBy(() -> userService.updateMyProfile("uid-1", buildRequest("중복닉", null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NICKNAME_ALREADY_EXISTS));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void 프로필_수정_소개_null이면_기존값_유지() {
        User user = buildUser("uid-1", "test@test.com", "기존닉네임", "기존소개", null);
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);

        UpdateProfileRequest request = buildRequest("새닉네임", null);
        UserProfileResponse result = userService.updateMyProfile("uid-1", request);

        assertThat(result.getNickname()).isEqualTo("새닉네임");
        assertThat(result.getAbout()).isEqualTo("기존소개");
    }

    @Test
    void 프로필_수정_소개_빈문자열_삭제_성공() {
        User user = buildUser("uid-1", "test@test.com", "기존닉네임", "기존소개", null);
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);

        UpdateProfileRequest request = buildRequest("새닉네임", "");
        UserProfileResponse result = userService.updateMyProfile("uid-1", request);

        assertThat(result.getAbout()).isEmpty();
    }

    @Test
    void 프로필_수정_사용자없음_예외() {
        given(userRepository.findById("uid-x")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateMyProfile("uid-x", buildRequest("닉", null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void 프로필_수정_탈퇴한_사용자_예외() {
        User user = buildUser("uid-1", "test@test.com", "닉네임", null, LocalDateTime.now());
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateMyProfile("uid-1", buildRequest("닉", null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.WITHDRAWN_USER));
    }

    // ─── changePassword ───────────────────────────────────────────────────

    @Test
    void 비밀번호_변경_성공() {
        User user = buildUser("uid-1", "test@test.com", "테스터", null, null);
        user.setPw("encoded_old");
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("oldPass1!", "encoded_old")).willReturn(true);
        given(passwordEncoder.encode("newPass1!")).willReturn("encoded_new");

        userService.changePassword("uid-1", buildChangePasswordRequest("oldPass1!", "newPass1!"));

        assertThat(user.getPw()).isEqualTo("encoded_new");
        verify(userRepository).save(user);
    }

    @Test
    void 비밀번호_변경_현재비밀번호_불일치_예외() {
        User user = buildUser("uid-1", "test@test.com", "테스터", null, null);
        user.setPw("encoded_old");
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongPass!", "encoded_old")).willReturn(false);

        assertThatThrownBy(() -> userService.changePassword("uid-1", buildChangePasswordRequest("wrongPass!", "newPass1!")))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.WRONG_CURRENT_PASSWORD));
    }

    @Test
    void 비밀번호_변경_새비밀번호가_현재와_동일_예외() {
        User user = buildUser("uid-1", "test@test.com", "테스터", null, null);
        user.setPw("encoded_old");
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));
        // 현재 비밀번호 일치 + 새 비밀번호도 같은 값이라 기존 해시와 매칭됨
        given(passwordEncoder.matches("samePass1!", "encoded_old")).willReturn(true);

        assertThatThrownBy(() -> userService.changePassword("uid-1", buildChangePasswordRequest("samePass1!", "samePass1!")))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SAME_AS_CURRENT_PASSWORD));
        verify(userRepository, never()).save(any());
    }

    @Test
    void 비밀번호_변경_사용자없음_예외() {
        given(userRepository.findById("uid-x")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword("uid-x", buildChangePasswordRequest("old", "newPass1!")))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void 비밀번호_변경_탈퇴한_사용자_예외() {
        User user = buildUser("uid-1", "test@test.com", "테스터", null, LocalDateTime.now());
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changePassword("uid-1", buildChangePasswordRequest("old", "newPass1!")))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.WITHDRAWN_USER));
    }

    // ─── withdraw ─────────────────────────────────────────────────────────

    @Test
    void 회원_탈퇴_성공() {
        User user = buildUser("uid-1", "test@test.com", "테스터", null, null);
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);

        userService.withdraw("uid-1");

        assertThat(user.getWithdrawalAt()).isNotNull();
        assertThat(user.getNick()).isNull();  // 닉네임 해제 → 재사용 가능
        verify(userRepository).save(user);
    }

    @Test
    void 회원_탈퇴_후_about_유지() {
        User user = buildUser("uid-1", "test@test.com", "테스터", "내 소개글", null);
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);

        userService.withdraw("uid-1");

        assertThat(user.getWithdrawalAt()).isNotNull();
        assertThat(user.getAbout()).isEqualTo("내 소개글");
    }

    @Test
    void 회원_탈퇴_사용자없음_예외() {
        given(userRepository.findById("uid-x")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.withdraw("uid-x"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void 회원_탈퇴_이미_탈퇴한_사용자_예외() {
        User user = buildUser("uid-1", "test@test.com", "테스터", null, LocalDateTime.now());
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.withdraw("uid-1"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.WITHDRAWN_USER));
    }

    // ─── uploadAvatar ─────────────────────────────────────────────────────

    @TempDir
    Path tempDir;

    @Test
    void 아바타_업로드_성공() {
        User user = buildUser("uid-1", "test@test.com", "테스터", null, null);
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);
        ReflectionTestUtils.setField(userService, "uploadDir", tempDir.toString());

        byte[] jpegBytes = new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0, 0x00, 0x10};
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes);
        String avatarUrl = userService.uploadAvatar("uid-1", file);

        assertThat(avatarUrl).startsWith("/avatars/").endsWith(".jpg");
        assertThat(user.getAvatarUrl()).isEqualTo(avatarUrl);
        verify(userRepository).save(user);
    }

    @Test
    void 아바타_업로드_이미지_아닌_파일_예외() {
        User user = buildUser("uid-1", "test@test.com", "테스터", null, null);
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "data".getBytes());

        assertThatThrownBy(() -> userService.uploadAvatar("uid-1", file))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_FILE_TYPE));
    }

    @Test
    void 아바타_업로드_사용자없음_예외() {
        given(userRepository.findById("uid-x")).willReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "img".getBytes());

        assertThatThrownBy(() -> userService.uploadAvatar("uid-x", file))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void 아바타_업로드_탈퇴한_사용자_예외() {
        User user = buildUser("uid-1", "test@test.com", "테스터", null, LocalDateTime.now());
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "img".getBytes());

        assertThatThrownBy(() -> userService.uploadAvatar("uid-1", file))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.WITHDRAWN_USER));
    }

    // ─── getUserPublicProfile ─────────────────────────────────────────────

    @Test
    void 공개_프로필_조회_성공() {
        User user = buildUser("uid-2", "other@test.com", "다른유저", "소개글", null);
        given(userRepository.findById("uid-2")).willReturn(Optional.of(user));

        UserProfileResponse result = userService.getUserPublicProfile("uid-2");

        assertThat(result.getUserId()).isEqualTo("uid-2");
        assertThat(result.getNickname()).isEqualTo("다른유저");
    }

    @Test
    void 공개_프로필_조회_탈퇴한사용자_404() {
        User user = buildUser("uid-2", "other@test.com", "탈퇴유저", null, LocalDateTime.now());
        given(userRepository.findById("uid-2")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.getUserPublicProfile("uid-2"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    // ─── deleteAvatar ─────────────────────────────────────────────────────

    @Test
    void 아바타_제거_성공_avatarUrl_null로() {
        User user = buildUser("uid-1", "test@test.com", "테스터", null, null);
        user.setAvatarUrl("/avatars/some.jpg");
        ReflectionTestUtils.setField(userService, "uploadDir", System.getProperty("java.io.tmpdir"));
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));

        userService.deleteAvatar("uid-1");

        assertThat(user.getAvatarUrl()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void 아바타_제거_사진없어도_성공() {
        User user = buildUser("uid-1", "test@test.com", "테스터", null, null);
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));

        userService.deleteAvatar("uid-1");

        assertThat(user.getAvatarUrl()).isNull();
    }

    @Test
    void 아바타_제거_사용자없음_예외() {
        given(userRepository.findById("none")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteAvatar("none"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void 아바타_제거_탈퇴한_사용자_예외() {
        User user = buildUser("uid-1", "test@test.com", "테스터", null, LocalDateTime.now());
        given(userRepository.findById("uid-1")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.deleteAvatar("uid-1"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.WITHDRAWN_USER));
    }

    // ─── isNicknameAvailable ──────────────────────────────────────────────

    @Test
    void 닉네임_사용가능_본인제외_중복없음_true() {
        given(userRepository.existsNickByOtherUser("새닉", "uid-1")).willReturn(false);
        assertThat(userService.isNicknameAvailable("uid-1", "새닉")).isTrue();
    }

    @Test
    void 닉네임_사용불가_다른유저가_사용중_false() {
        given(userRepository.existsNickByOtherUser("중복닉", "uid-1")).willReturn(true);
        assertThat(userService.isNicknameAvailable("uid-1", "중복닉")).isFalse();
    }

    private ChangePasswordRequest buildChangePasswordRequest(String currentPassword, String newPassword) {
        ChangePasswordRequest req = new ChangePasswordRequest();
        ReflectionTestUtils.setField(req, "currentPassword", currentPassword);
        ReflectionTestUtils.setField(req, "newPassword", newPassword);
        return req;
    }

    private UpdateProfileRequest buildRequest(String nickname, String about) {
        UpdateProfileRequest req = new UpdateProfileRequest();
        ReflectionTestUtils.setField(req, "nickname", nickname);
        ReflectionTestUtils.setField(req, "about", about);
        return req;
    }

    private User buildUser(String id, String email, String nick, String about, LocalDateTime withdrawalAt) {
        User user = new User();
        user.setUserId(id);
        user.setNick(nick);
        user.setAbout(about);
        user.setJoinAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        user.setWithdrawalAt(withdrawalAt);
        return user;
    }
}
