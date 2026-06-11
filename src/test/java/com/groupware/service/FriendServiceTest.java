package com.groupware.service;

import com.groupware.domain.Friend;
import com.groupware.domain.User;
import com.groupware.dto.friend.FriendRequestDto;
import com.groupware.dto.friend.FriendResponse;
import com.groupware.dto.notification.NotificationPayload;
import com.groupware.dto.user.UserSearchResponse;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.FriendRepository;
import com.groupware.repository.UserRepository;
import com.groupware.websocket.WebSocketEventListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FriendServiceTest {

    @InjectMocks private FriendService friendService;
    @Mock private FriendRepository friendRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private WebSocketEventListener wsEventListener;
    @Mock private TestBotService testBotService;

    // ─── searchUsers ──────────────────────────────────────────────────────

    @Test
    void 사용자_검색_자신_제외() {
        User other = buildUser("uid-2", "other@test.com", "다른유저");
        given(userRepository.searchByNickOrId("유저")).willReturn(List.of(other));

        List<UserSearchResponse> result = friendService.searchUsers("유저", "uid-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo("uid-2");
    }

    @Test
    void 사용자_검색_자신_포함시_제외됨() {
        User me = buildUser("uid-1", "me@test.com", "나");
        given(userRepository.searchByNickOrId("나")).willReturn(List.of(me));

        List<UserSearchResponse> result = friendService.searchUsers("나", "uid-1");

        assertThat(result).isEmpty();
    }

    // ─── sendRequest ──────────────────────────────────────────────────────

    @Test
    void 친구_요청_성공() {
        User me = buildUser("uid-1", "me@test.com", "나");
        User target = buildUser("uid-2", "other@test.com", "상대");
        FriendRequestDto dto = friendRequestDto("uid-2");

        given(userRepository.findById("uid-1")).willReturn(Optional.of(me));
        given(userRepository.findById("uid-2")).willReturn(Optional.of(target));
        given(friendRepository.existsRelationship(me, target)).willReturn(false);

        friendService.sendRequest(dto, "uid-1");

        verify(friendRepository).save(any(Friend.class));
        verify(messagingTemplate).convertAndSendToUser(
                eq("uid-2"), eq("/queue/notifications"), any(NotificationPayload.class));
    }

    @Test
    void 자기_자신에게_요청_예외() {
        FriendRequestDto dto = friendRequestDto("uid-1");

        assertThatThrownBy(() -> friendService.sendRequest(dto, "uid-1"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CANNOT_ADD_SELF));
    }

    @Test
    void 이미_친구_관계_존재시_요청_예외() {
        User me = buildUser("uid-1", "me@test.com", "나");
        User target = buildUser("uid-2", "other@test.com", "상대");
        FriendRequestDto dto = friendRequestDto("uid-2");

        given(userRepository.findById("uid-1")).willReturn(Optional.of(me));
        given(userRepository.findById("uid-2")).willReturn(Optional.of(target));
        given(friendRepository.existsRelationship(me, target)).willReturn(true);

        assertThatThrownBy(() -> friendService.sendRequest(dto, "uid-1"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.FRIEND_REQUEST_ALREADY_EXISTS));
    }

    @Test
    void 존재하지_않는_대상_요청_예외() {
        User me = buildUser("uid-1", "me@test.com", "나");
        FriendRequestDto dto = friendRequestDto("uid-999");

        given(userRepository.findById("uid-1")).willReturn(Optional.of(me));
        given(userRepository.findById("uid-999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> friendService.sendRequest(dto, "uid-1"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    // ─── acceptRequest ────────────────────────────────────────────────────

    @Test
    void 친구_요청_수락() {
        User me = buildUser("uid-2", "me@test.com", "나");
        Friend pending = buildFriend(1L, buildUser("uid-1", "a@b.com", "요청자"), me, "PENDING");

        given(userRepository.findById("uid-2")).willReturn(Optional.of(me));
        given(friendRepository.findByFriendIdxAndFriend(1L, me)).willReturn(Optional.of(pending));

        friendService.acceptRequest(1L, "uid-2");

        assertThat(pending.getStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    void 존재하지_않는_요청_수락_예외() {
        User me = buildUser("uid-2", "me@test.com", "나");

        given(userRepository.findById("uid-2")).willReturn(Optional.of(me));
        given(friendRepository.findByFriendIdxAndFriend(99L, me)).willReturn(Optional.empty());

        assertThatThrownBy(() -> friendService.acceptRequest(99L, "uid-2"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.FRIEND_REQUEST_NOT_FOUND));
    }

    // ─── rejectRequest ────────────────────────────────────────────────────

    @Test
    void 친구_요청_거절시_삭제() {
        User me = buildUser("uid-2", "me@test.com", "나");
        Friend pending = buildFriend(1L, buildUser("uid-1", "a@b.com", "요청자"), me, "PENDING");

        given(userRepository.findById("uid-2")).willReturn(Optional.of(me));
        given(friendRepository.findByFriendIdxAndFriend(1L, me)).willReturn(Optional.of(pending));

        friendService.rejectRequest(1L, "uid-2");

        verify(friendRepository).delete(pending);
    }

    // ─── deleteFriend ─────────────────────────────────────────────────────

    @Test
    void 친구_삭제() {
        User me = buildUser("uid-1", "me@test.com", "나");
        Friend accepted = buildFriend(1L, me, buildUser("uid-2", "a@b.com", "상대"), "ACCEPTED");

        given(userRepository.findById("uid-1")).willReturn(Optional.of(me));
        given(friendRepository.findAcceptedByIdxAndUser(1L, me)).willReturn(Optional.of(accepted));

        friendService.deleteFriend(1L, "uid-1");

        verify(friendRepository).delete(accepted);
    }

    @Test
    void 존재하지_않는_친구_삭제_예외() {
        User me = buildUser("uid-1", "me@test.com", "나");

        given(userRepository.findById("uid-1")).willReturn(Optional.of(me));
        given(friendRepository.findAcceptedByIdxAndUser(99L, me)).willReturn(Optional.empty());

        assertThatThrownBy(() -> friendService.deleteFriend(99L, "uid-1"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.FRIEND_NOT_FOUND));
    }

    // ─── getFriends ───────────────────────────────────────────────────────

    @Test
    void 친구_목록_조회() {
        User me = buildUser("uid-1", "me@test.com", "나");
        User other = buildUser("uid-2", "a@b.com", "친구");
        Friend accepted = buildFriend(1L, me, other, "ACCEPTED");

        given(userRepository.findById("uid-1")).willReturn(Optional.of(me));
        given(friendRepository.findAcceptedFriends(me)).willReturn(List.of(accepted));

        List<FriendResponse> result = friendService.getFriends("uid-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo("uid-2");
    }

    // ─── getPendingRequests ───────────────────────────────────────────────

    @Test
    void 받은_친구_요청_목록_조회() {
        User me = buildUser("uid-2", "me@test.com", "나");
        User requester = buildUser("uid-1", "a@b.com", "요청자");
        Friend pending = buildFriend(1L, requester, me, "PENDING");

        given(userRepository.findById("uid-2")).willReturn(Optional.of(me));
        given(friendRepository.findByFriendAndStatus(me, "PENDING")).willReturn(List.of(pending));

        List<FriendResponse> result = friendService.getPendingRequests("uid-2");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo("uid-1");
        assertThat(result.get(0).getStatus()).isEqualTo("PENDING");
    }

    // ─── getOnlineStatuses ────────────────────────────────────────────────

    @Test
    void 친구_온라인_상태_조회() {
        User me = buildUser("uid-1", "me@test.com", "나");
        User onlineFriend = buildUser("uid-2", "a@b.com", "온라인친구");
        User offlineFriend = buildUser("uid-3", "b@b.com", "오프라인친구");
        Friend f1 = buildFriend(1L, me, onlineFriend, "ACCEPTED");
        Friend f2 = buildFriend(2L, me, offlineFriend, "ACCEPTED");

        given(userRepository.findById("uid-1")).willReturn(Optional.of(me));
        given(friendRepository.findAcceptedFriends(me)).willReturn(List.of(f1, f2));
        given(wsEventListener.isOnline("uid-2")).willReturn(true);
        given(wsEventListener.isOnline("uid-3")).willReturn(false);

        Map<String, Boolean> result = friendService.getOnlineStatuses("uid-1");

        assertThat(result).containsEntry("uid-2", true);
        assertThat(result).containsEntry("uid-3", false);
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private User buildUser(String userId, String email, String nick) {
        User u = new User();
        u.setUserId(userId);
        u.setNick(nick);
        return u;
    }

    private Friend buildFriend(Long idx, User user, User friend, String status) {
        Friend f = new Friend();
        ReflectionTestUtils.setField(f, "friendIdx", idx);
        f.setUser(user);
        f.setFriend(friend);
        f.setStatus(status);
        return f;
    }

    private FriendRequestDto friendRequestDto(String targetUserId) {
        FriendRequestDto dto = new FriendRequestDto();
        ReflectionTestUtils.setField(dto, "targetUserId", targetUserId);
        return dto;
    }
}
