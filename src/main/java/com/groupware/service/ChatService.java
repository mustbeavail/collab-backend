package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.Message;
import com.groupware.domain.RoomMember;
import com.groupware.domain.User;
import com.groupware.dto.chat.ChatMessagePayload;
import com.groupware.dto.chat.ChatRoomDetailResponse;
import com.groupware.dto.chat.ChatRoomResponse;
import com.groupware.dto.chat.InviteRequest;
import com.groupware.dto.chat.MessagePageResponse;
import com.groupware.dto.chat.MessageResponse;
import com.groupware.dto.chat.RoomMemberResponse;
import com.groupware.dto.chat.SendMessageRequest;
import com.groupware.dto.chat.UpdateRoomInfoRequest;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.MessageRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.TeamMemberRepository;
import com.groupware.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int DEFAULT_PAGE_SIZE = 50;

    private final ChatRoomRepository chatRoomRepository;
    private final MessageRepository messageRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public MessagePageResponse getMessages(String userId, Long roomIdx, Long before, int size) {
        ChatRoom room = chatRoomRepository.findById(roomIdx)
                .filter(r -> r.getDelDate() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        checkAccess(room, user);

        // size+1 개 조회 → hasMore 판단 후 초과분 제거
        PageRequest pageable = PageRequest.of(0, size + 1);
        List<Message> fetched = (before == null)
                ? messageRepository.findLatestMessages(room, pageable)
                : messageRepository.findMessagesBefore(room, before, pageable);

        boolean hasMore = fetched.size() > size;
        List<Message> page = hasMore ? fetched.subList(0, size) : fetched;

        // DESC 쿼리 결과를 ASC(오래된→최신) 순으로 뒤집어 반환
        List<MessageResponse> messages = new ArrayList<>(
                page.stream().map(MessageResponse::from).toList()
        );
        Collections.reverse(messages);

        return MessagePageResponse.builder()
                .messages(messages)
                .hasMore(hasMore)
                .build();
    }

    @Transactional
    public void sendMessage(String userId, Long roomIdx, SendMessageRequest request) {
        ChatRoom room = chatRoomRepository.findById(roomIdx)
                .filter(r -> r.getDelDate() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        checkAccess(room, user);

        Message msg = new Message();
        msg.setChatRoom(room);
        msg.setUser(user);
        msg.setContent(request.getContent());
        msg.setMsgType(request.getMsgType() != null ? request.getMsgType() : "TEXT");
        msg.setSentAt(LocalDateTime.now());
        msg.setDelYn(false);
        msg = messageRepository.save(msg);

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .msgIdx(msg.getMsgIdx())
                .roomIdx(roomIdx)
                .userId(user.getUserId())
                .nickname(user.getNick())
                .avatarUrl(user.getAvatarUrl())
                .content(msg.getContent())
                .msgType(msg.getMsgType())
                .sentAt(msg.getSentAt())
                .build();

        messagingTemplate.convertAndSend("/topic/room/" + roomIdx, payload);
    }

    @Transactional
    public ChatRoomResponse getOrCreateDmRoom(String userId, String targetUserId) {
        User me = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return chatRoomRepository.findDmRoom(me, target)
                .map(ChatRoomResponse::from)
                .orElseGet(() -> ChatRoomResponse.from(createDmRoom(me, target)));
    }

    @Transactional(readOnly = true)
    public List<RoomMemberResponse> getRoomMembers(String userId, Long roomIdx) {
        ChatRoom room = chatRoomRepository.findById(roomIdx)
                .filter(r -> r.getDelDate() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        checkAccess(room, user);

        if (room.getTeam() != null) {
            return teamMemberRepository.findActiveByTeamIdx(room.getTeam().getTeamIdx())
                    .stream()
                    .map(tm -> RoomMemberResponse.builder()
                            .userId(tm.getUser().getUserId())
                            .nickname(tm.getUser().getNick())
                            .avatarUrl(tm.getUser().getAvatarUrl())
                            .role(tm.getRole())
                            .build())
                    .toList();
        }

        return roomMemberRepository.findAllActiveByRoom(room)
                .stream()
                .map(RoomMemberResponse::from)
                .toList();
    }

    @Transactional
    public void inviteToRoom(String userId, Long roomIdx, InviteRequest request) {
        ChatRoom room = chatRoomRepository.findById(roomIdx)
                .filter(r -> r.getDelDate() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (room.getTeam() != null) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }

        User inviter = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, inviter)) {
            throw new CustomException(ErrorCode.NOT_ROOM_MEMBER);
        }

        User target = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, target)) {
            return;
        }

        RoomMember member = new RoomMember();
        member.setChatRoom(room);
        member.setUser(target);
        member.setRole("MEMBER");
        member.setJoinAt(LocalDateTime.now());
        roomMemberRepository.save(member);
    }

    @Transactional
    public void leaveRoom(String userId, Long roomIdx) {
        ChatRoom room = chatRoomRepository.findById(roomIdx)
                .filter(r -> r.getDelDate() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (room.getTeam() != null) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        RoomMember member = roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, user)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_ROOM_MEMBER));

        member.setExitAt(LocalDateTime.now());
        roomMemberRepository.save(member);
    }

    @Transactional
    public void kickMember(String userId, Long roomIdx, String targetUserId) {
        ChatRoom room = chatRoomRepository.findById(roomIdx)
                .filter(r -> r.getDelDate() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (room.getTeam() != null) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }

        User kicker = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        RoomMember kickerMember = roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, kicker)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_ROOM_MEMBER));

        if (!"OWNER".equals(kickerMember.getRole())) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }

        if (userId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        RoomMember targetMember = roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, target)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_ROOM_MEMBER));

        targetMember.setExitAt(LocalDateTime.now());
        roomMemberRepository.save(targetMember);
    }

    @Transactional(readOnly = true)
    public ChatRoomDetailResponse getRoomInfo(String userId, Long roomIdx) {
        ChatRoom room = chatRoomRepository.findById(roomIdx)
                .filter(r -> r.getDelDate() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        checkAccess(room, user);
        return ChatRoomDetailResponse.from(room);
    }

    @Transactional
    public ChatRoomDetailResponse updateRoomInfo(String userId, Long roomIdx, UpdateRoomInfoRequest request) {
        ChatRoom room = chatRoomRepository.findById(roomIdx)
                .filter(r -> r.getDelDate() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (room.getTeam() != null) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        RoomMember member = roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, user)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_ROOM_MEMBER));
        if (!"OWNER".equals(member.getRole())) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }
        room.setRoomName(request.getRoomName());
        room.setDescription(request.getDescription());
        chatRoomRepository.save(room);
        return ChatRoomDetailResponse.from(room);
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

    private ChatRoom createDmRoom(User u1, User u2) {
        ChatRoom room = new ChatRoom();
        room.setRoomName(u1.getNick() + ", " + u2.getNick());
        room.setCreatedAt(LocalDateTime.now());
        room = chatRoomRepository.save(room);

        RoomMember m1 = new RoomMember();
        m1.setChatRoom(room);
        m1.setUser(u1);
        m1.setRole("OWNER");
        m1.setJoinAt(LocalDateTime.now());
        roomMemberRepository.save(m1);

        RoomMember m2 = new RoomMember();
        m2.setChatRoom(room);
        m2.setUser(u2);
        m2.setRole("MEMBER");
        m2.setJoinAt(LocalDateTime.now());
        roomMemberRepository.save(m2);

        return room;
    }
}
