package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.Message;
import com.groupware.domain.RoomMember;
import com.groupware.domain.TeamMember;
import com.groupware.domain.User;
import com.groupware.dto.chat.ChatMessagePayload;
import com.groupware.dto.chat.ChatRoomDetailResponse;
import com.groupware.dto.chat.ChatRoomResponse;
import com.groupware.dto.chat.DmRoomResponse;
import com.groupware.dto.chat.InviteRequest;
import com.groupware.dto.notification.NotificationPayload;
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
import org.springframework.transaction.annotation.Isolation;
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
    private final TestBotService testBotService;

    @Transactional(readOnly = true)
    public MessagePageResponse getMessages(String userId, Long roomIdx, Long before, int size) {
        ChatRoom room = getActiveRoom(roomIdx);
        User user = getActiveUser(userId);
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
        ChatRoom room = getActiveRoom(roomIdx);
        User user = getActiveUser(userId);
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

        // 미개방 방에도 알림 가도록 각 멤버 user-queue로 NEW_MESSAGE 푸시(qa 항목15: 알림 없던 문제)
        notifyRoomMembers(room, user, msg.getContent());

        // 테스트봇이 참여한 DM이면 비동기로 답장 생성 (봇 자신의 메시지는 내부에서 무시)
        testBotService.maybeAutoReply(roomIdx, msg.getMsgIdx(), user.getUserId(), user.getNick(), msg.getContent());
    }

    private void notifyRoomMembers(ChatRoom room, User sender, String content) {
        String preview = (content != null && content.length() > 50) ? content.substring(0, 50) : content;
        roomMemberRepository.findAllActiveByRoom(room).stream()
                .map(RoomMember::getUser)
                .filter(u -> !u.getUserId().equals(sender.getUserId()))
                .forEach(u -> messagingTemplate.convertAndSendToUser(
                        u.getUserId(),
                        "/queue/notifications",
                        NotificationPayload.builder()
                                .type("NEW_MESSAGE")
                                .roomIdx(room.getRoomIdx())
                                .userId(sender.getUserId())
                                .nickname(sender.getNick())
                                .content(preview)
                                .roomName(room.getRoomName())
                                .build()));
    }

    @Transactional(readOnly = true)
    public List<DmRoomResponse> getMyDmRooms(String userId) {
        User me = getActiveUser(userId);
        return chatRoomRepository.findMyDmRooms(userId).stream().map(room -> {
            List<RoomMember> members = roomMemberRepository.findAllActiveByRoom(room);
            boolean owner = members.stream()
                    .anyMatch(m -> "OWNER".equals(m.getRole()) && m.getUser().getUserId().equals(userId));
            boolean custom = Boolean.TRUE.equals(room.getCustomName());
            if (members.size() == 2) {
                User other = members.stream().map(RoomMember::getUser)
                        .filter(u -> !u.getUserId().equals(userId))
                        .findFirst().orElse(me);
                // 이름을 직접 변경한 DM이면 커스텀 이름, 아니면 상대 닉네임(기본)
                String displayName = custom && room.getRoomName() != null
                        ? room.getRoomName()
                        : (other.getNick() != null ? other.getNick() : "(탈퇴한 회원)");
                return DmRoomResponse.builder()
                        .roomIdx(room.getRoomIdx())
                        .name(displayName)
                        .dm(true)
                        .targetUserId(other.getUserId())
                        .avatarUrl(other.getAvatarUrl())
                        .memberCount(members.size())
                        .owner(owner)
                        .build();
            }
            return DmRoomResponse.builder()
                    .roomIdx(room.getRoomIdx())
                    .name(room.getRoomName())
                    .dm(false)
                    .memberCount(members.size())
                    .owner(owner)
                    .build();
        }).toList();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ChatRoomResponse getOrCreateDmRoom(String userId, String targetUserId) {
        User me = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 카카오톡식 로직(I-2): 현재 활성 멤버가 정확히 {나, 상대} 2명인 방만 재사용한다.
        // 한 명이 나가 1명만 남은 방(휴면방)은 재사용하지 않고 새 방을 만든다.
        // 3명 이상으로 늘릴 때는 별도 로직(inviteToRoom)으로 기존 방을 유지한 채 인원만 늘린다.
        return chatRoomRepository.findDmRoom(me, target)
                .map(ChatRoomResponse::from)
                .orElseGet(() -> ChatRoomResponse.from(createDmRoom(me, target)));
    }

    @Transactional(readOnly = true)
    public List<RoomMemberResponse> getRoomMembers(String userId, Long roomIdx) {
        ChatRoom room = getActiveRoom(roomIdx);
        User user = getActiveUser(userId);
        checkAccess(room, user);

        // 팀방/일반방 구분 없이 실제 참가 멤버만 반환(제대로안된것1: 팀원 전체가 멤버로 뜨던 문제)
        return roomMemberRepository.findAllActiveByRoom(room)
                .stream()
                .map(RoomMemberResponse::from)
                .toList();
    }

    @Transactional
    public void inviteToRoom(String userId, Long roomIdx, InviteRequest request) {
        ChatRoom room = getActiveRoom(roomIdx);
        User inviter = getActiveUser(userId);
        User target = getActiveUser(request.getUserId());

        if (room.getTeam() != null) {
            // 팀 채팅방: 초대는 권한자(LEADER/MANAGER)만, 대상은 팀 멤버여야(버그 항목17)
            Long teamIdx = room.getTeam().getTeamIdx();
            TeamMember inviterMember = teamMemberRepository.findActiveByTeamIdxAndUserId(teamIdx, userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAM_MEMBER));
            if ("MEMBER".equals(inviterMember.getRole())) {
                throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
            }
            teamMemberRepository.findActiveByTeamIdxAndUserId(teamIdx, target.getUserId())
                    .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAM_MEMBER));
        } else {
            // DM/그룹: 초대자가 방 멤버여야(대상은 전체 유저 가능)
            if (!roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, inviter)) {
                throw new CustomException(ErrorCode.NOT_ROOM_MEMBER);
            }
        }

        if (roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, target)) {
            return;
        }

        RoomMember member = new RoomMember();
        member.setChatRoom(room);
        member.setUser(target);
        member.setRole("MEMBER");
        member.setJoinAt(LocalDateTime.now());
        roomMemberRepository.save(member);

        // 입장 시스템 메시지(E *추가)
        broadcastSystemMessage(room, target, target.getNick() + "님이 입장했습니다.");
    }

    @Transactional
    public void leaveRoom(String userId, Long roomIdx) {
        // 채팅방 일원화: 팀 채팅방도 일반 채팅방과 동일하게 나가기 허용(버그 항목16/26)
        ChatRoom room = getActiveRoom(roomIdx);
        User user = getActiveUser(userId);

        RoomMember member = roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, user)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_ROOM_MEMBER));

        member.setExitAt(LocalDateTime.now());
        roomMemberRepository.save(member);

        // 퇴장 시스템 메시지(E *추가)
        broadcastSystemMessage(room, user, user.getNick() + "님이 퇴장했습니다.");

        // 카카오톡식 로직(I-2): 남은 활성 멤버가 0명이면 채팅방 소프트딜리트
        // (1명만 남으면 휴면방으로 그 1명에게만 유지된다)
        softDeleteIfEmpty(room);
    }

    /** 활성 멤버가 0명이면 채팅방을 소프트딜리트(I-2). */
    private void softDeleteIfEmpty(ChatRoom room) {
        if (room.getTeam() != null) return; // 팀 채팅방은 멤버 0이어도 유지(팀 소유)
        if (roomMemberRepository.findAllActiveByRoom(room).isEmpty()) {
            room.setDelDate(LocalDateTime.now());
            chatRoomRepository.save(room);
        }
    }

    /** 팀 채팅방 삭제(소프트). 팀 LEADER만 가능. */
    @Transactional
    public void deleteRoom(String userId, Long roomIdx) {
        ChatRoom room = getActiveRoom(roomIdx);
        if (room.getTeam() == null) {
            // 팀 채팅방만 삭제 대상(일반/DM은 나가기로 처리)
            throw new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }
        TeamMember member = teamMemberRepository
                .findActiveByTeamIdxAndUserId(room.getTeam().getTeamIdx(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAM_MEMBER));
        if (!"LEADER".equals(member.getRole())) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }
        room.setDelDate(LocalDateTime.now());
        chatRoomRepository.save(room);
    }

    @Transactional
    public void kickMember(String userId, Long roomIdx, String targetUserId) {
        ChatRoom room = getActiveRoom(roomIdx);
        if (room.getTeam() != null) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }

        User kicker = getActiveUser(userId);

        RoomMember kickerMember = roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, kicker)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_ROOM_MEMBER));

        if (!"OWNER".equals(kickerMember.getRole())) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }

        if (userId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }

        User target = getActiveUser(targetUserId);

        RoomMember targetMember = roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, target)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_ROOM_MEMBER));

        targetMember.setExitAt(LocalDateTime.now());
        roomMemberRepository.save(targetMember);

        // 추방 후 활성 멤버 0명이면 소프트딜리트(I-2)
        softDeleteIfEmpty(room);
    }

    @Transactional(readOnly = true)
    public ChatRoomDetailResponse getRoomInfo(String userId, Long roomIdx) {
        ChatRoom room = getActiveRoom(roomIdx);
        User user = getActiveUser(userId);
        checkAccess(room, user);
        int memberCount = roomMemberRepository.findAllActiveByRoom(room).size();
        String myRole = roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, user)
                .map(RoomMember::getRole).orElse(null);
        return ChatRoomDetailResponse.from(room, memberCount, myRole);
    }

    @Transactional
    public ChatRoomDetailResponse updateRoomInfo(String userId, Long roomIdx, UpdateRoomInfoRequest request) {
        // 채팅방 이름 변경(I-13). 팀/일반 구분 없이 방 OWNER만 가능.
        ChatRoom room = getActiveRoom(roomIdx);
        User user = getActiveUser(userId);
        RoomMember member = roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, user)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_ROOM_MEMBER));
        if (!"OWNER".equals(member.getRole())) {
            throw new CustomException(ErrorCode.TEAM_ACCESS_DENIED);
        }
        room.setRoomName(request.getRoomName());
        room.setCustomName(true); // 직접 변경됨 → 2인 DM도 사이드바에 커스텀 이름 표시
        chatRoomRepository.save(room);

        // 변경된 이름을 방 멤버 전원에게 실시간 전파(I-13: 모든 패널·열린 창 즉시 반영)
        List<RoomMember> members = roomMemberRepository.findAllActiveByRoom(room);
        broadcastRoomRenamed(room, members);
        return ChatRoomDetailResponse.from(room, members.size(), "OWNER");
    }

    private void broadcastRoomRenamed(ChatRoom room, List<RoomMember> members) {
        members.stream()
                .map(RoomMember::getUser)
                .forEach(u -> messagingTemplate.convertAndSendToUser(
                        u.getUserId(),
                        "/queue/notifications",
                        NotificationPayload.builder()
                                .type("ROOM_RENAMED")
                                .roomIdx(room.getRoomIdx())
                                .roomName(room.getRoomName())
                                .build()));
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
        // 채팅방 일원화: 팀방도 실제 방 멤버만 접근(팀원이어도 미참가면 입장 불가, 버그 항목17/제대로안된것1)
        if (!roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)) {
            throw new CustomException(ErrorCode.NOT_ROOM_MEMBER);
        }
    }

    private void broadcastSystemMessage(ChatRoom room, User actor, String text) {
        Message sysMsg = new Message();
        sysMsg.setChatRoom(room);
        sysMsg.setUser(actor);
        sysMsg.setContent(text);
        sysMsg.setMsgType("SYSTEM");
        sysMsg.setSentAt(LocalDateTime.now());
        sysMsg.setDelYn(false);
        sysMsg = messageRepository.save(sysMsg);

        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomIdx(),
                ChatMessagePayload.builder()
                        .msgIdx(sysMsg.getMsgIdx())
                        .roomIdx(room.getRoomIdx())
                        .userId(actor.getUserId())
                        .nickname(actor.getNick())
                        .content(text)
                        .msgType("SYSTEM")
                        .sentAt(sysMsg.getSentAt())
                        .build());
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
