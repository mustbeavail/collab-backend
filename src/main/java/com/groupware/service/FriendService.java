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
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRepository friendRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketEventListener wsEventListener;
    private final TestBotService testBotService;

    public List<UserSearchResponse> searchUsers(String q, String myUserId) {
        return userRepository.searchByNickOrId(q).stream()
                .filter(u -> !u.getUserId().equals(myUserId))
                .map(UserSearchResponse::from)
                .toList();
    }

    @Transactional
    public void sendRequest(FriendRequestDto dto, String myUserId) {
        if (myUserId.equals(dto.getTargetUserId())) {
            throw new CustomException(ErrorCode.CANNOT_ADD_SELF);
        }

        User me = getUser(myUserId);
        User target = userRepository.findById(dto.getTargetUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (friendRepository.existsRelationship(me, target)) {
            throw new CustomException(ErrorCode.FRIEND_REQUEST_ALREADY_EXISTS);
        }

        Friend request = new Friend();
        request.setUser(me);
        request.setFriend(target);
        request.setStatus("PENDING");
        friendRepository.save(request);

        messagingTemplate.convertAndSendToUser(
                target.getUserId(),
                "/queue/notifications",
                NotificationPayload.builder()
                        .type("FRIEND_REQUEST")
                        .friendIdx(request.getFriendIdx())
                        .userId(me.getUserId())
                        .nickname(me.getNick())
                        .email(me.getUserId())
                        .status("PENDING")
                        .avatarUrl(me.getAvatarUrl())
                        .build()
        );
    }

    @Transactional
    public void acceptRequest(Long friendIdx, String myUserId) {
        User me = getUser(myUserId);
        Friend request = friendRepository.findByFriendIdxAndFriend(friendIdx, me)
                .orElseThrow(() -> new CustomException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));
        request.setStatus("ACCEPTED");

        // 테스트봇의 요청을 수락한 경우 → 봇이 DM 방을 만들고 안내 메시지를 보낸다
        if (testBotService.isBotId(request.getUser().getUserId())) {
            testBotService.onFriendshipWithBotAccepted(me.getUserId());
        }
    }

    @Transactional
    public void rejectRequest(Long friendIdx, String myUserId) {
        User me = getUser(myUserId);
        Friend request = friendRepository.findByFriendIdxAndFriend(friendIdx, me)
                .orElseThrow(() -> new CustomException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));
        friendRepository.delete(request);
    }

    @Transactional
    public void deleteFriend(Long friendIdx, String myUserId) {
        User me = getUser(myUserId);
        Friend friend = friendRepository.findAcceptedByIdxAndUser(friendIdx, me)
                .orElseThrow(() -> new CustomException(ErrorCode.FRIEND_NOT_FOUND));
        friendRepository.delete(friend);
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> getFriends(String myUserId) {
        User me = getUser(myUserId);
        return friendRepository.findAcceptedFriends(me).stream()
                .map(f -> FriendResponse.of(f, me))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> getPendingRequests(String myUserId) {
        User me = getUser(myUserId);
        return friendRepository.findByFriendAndStatus(me, "PENDING").stream()
                .map(f -> FriendResponse.of(f, me))
                .toList();
    }

    // 내가 보냈지만 아직 수락/거절 안 된 요청 목록(qa 항목6) — 친구패널에 '대기중'으로 표시
    @Transactional(readOnly = true)
    public List<FriendResponse> getSentRequests(String myUserId) {
        User me = getUser(myUserId);
        return friendRepository.findByUserAndStatus(me, "PENDING").stream()
                .map(f -> FriendResponse.of(f, me))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> getOnlineStatuses(String myUserId) {
        List<FriendResponse> friends = getFriends(myUserId);
        Map<String, Boolean> result = new HashMap<>();
        friends.forEach(f -> result.put(f.getUserId(), wsEventListener.isOnline(f.getUserId())));
        return result;
    }

    private User getUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
