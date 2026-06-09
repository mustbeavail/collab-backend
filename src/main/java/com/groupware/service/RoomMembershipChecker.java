package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.User;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.TeamMemberRepository;
import com.groupware.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅방/팀 채널 멤버십 검증 단일 진입점.
 *
 * <p>팀에 연결된 방이면 팀 멤버십(teamMember)으로, 아니면 방 멤버십(roomMember)으로 판단한다.
 * WebSocket 구독 인터셉터, 음성/차트 발행 등 roomIdx 기반 진입점에서 재사용하여
 * 멤버십 판단 로직이 여러 곳에 흩어지는 것을 막는다.
 */
@Component
@RequiredArgsConstructor
public class RoomMembershipChecker {

    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    /**
     * roomIdx 방에 userId가 활성 멤버인지 검증. 아니면 예외를 던진다.
     * 방/유저 미존재도 예외. (발행 컨트롤러·서비스에서 사용)
     */
    @Transactional(readOnly = true)
    public void check(Long roomIdx, String userId) {
        ChatRoom room = chatRoomRepository.findById(roomIdx)
                .filter(r -> r.getDelDate() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!isMember(room, user)) {
            throw new CustomException(
                    room.getTeam() != null ? ErrorCode.NOT_TEAM_MEMBER : ErrorCode.NOT_ROOM_MEMBER);
        }
    }

    /**
     * 예외 없이 boolean 으로 멤버 여부 반환. 방/유저 미존재 또는 비멤버면 false.
     * 자체 예외(MessagingException 등)를 던지는 쪽(구독 인터셉터)에서 사용.
     */
    @Transactional(readOnly = true)
    public boolean isMember(Long roomIdx, String userId) {
        ChatRoom room = chatRoomRepository.findById(roomIdx)
                .filter(r -> r.getDelDate() == null)
                .orElse(null);
        if (room == null) return false;
        return userRepository.findById(userId)
                .map(user -> isMember(room, user))
                .orElse(false);
    }

    private boolean isMember(ChatRoom room, User user) {
        if (room.getTeam() != null) {
            return teamMemberRepository
                    .findActiveByTeamIdxAndUserId(room.getTeam().getTeamIdx(), user.getUserId())
                    .isPresent();
        }
        return roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user);
    }
}
