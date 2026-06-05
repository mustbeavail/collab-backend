package com.groupware.service;

import com.groupware.domain.Friend;
import com.groupware.domain.User;
import com.groupware.dto.friend.FriendRequestDto;
import com.groupware.dto.friend.FriendResponse;
import com.groupware.dto.user.UserSearchResponse;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.FriendRepository;
import com.groupware.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRepository friendRepository;
    private final UserRepository userRepository;

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
    }

    @Transactional
    public void acceptRequest(Long friendIdx, String myUserId) {
        User me = getUser(myUserId);
        Friend request = friendRepository.findByFriendIdxAndFriend(friendIdx, me)
                .orElseThrow(() -> new CustomException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));
        request.setStatus("ACCEPTED");
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

    private User getUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
