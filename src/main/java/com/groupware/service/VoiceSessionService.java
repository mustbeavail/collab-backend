package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.User;
import com.groupware.dto.voice.SignalingMessage;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class VoiceSessionService {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;

    // roomIdx → 현재 음성 세션 참여자 userId Set (인메모리, 단일 인스턴스 기준)
    private final ConcurrentHashMap<Long, Set<String>> roomParticipants = new ConcurrentHashMap<>();

    public void join(Long roomIdx, String userId, String sessionType) {
        ChatRoom room = chatRoomRepository.findById(roomIdx)
                .filter(r -> r.getDelDate() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)) {
            throw new CustomException(ErrorCode.NOT_ROOM_MEMBER);
        }

        roomParticipants.computeIfAbsent(roomIdx, k -> ConcurrentHashMap.newKeySet()).add(userId);

        SignalingMessage msg = new SignalingMessage();
        msg.setType("JOIN");
        msg.setFromUserId(userId);
        msg.setFromNickname(user.getNick());
        msg.setSessionType(sessionType != null ? sessionType : "VOICE");
        messagingTemplate.convertAndSend("/topic/voice/" + roomIdx, msg);
    }

    public void leave(Long roomIdx, String userId) {
        Set<String> parts = roomParticipants.get(roomIdx);
        if (parts != null) {
            parts.remove(userId);
            if (parts.isEmpty()) roomParticipants.remove(roomIdx);
        }

        userRepository.findById(userId).ifPresent(user -> {
            SignalingMessage msg = new SignalingMessage();
            msg.setType("LEAVE");
            msg.setFromUserId(userId);
            msg.setFromNickname(user.getNick());
            messagingTemplate.convertAndSend("/topic/voice/" + roomIdx, msg);
        });
    }

    public void signal(Long roomIdx, String fromUserId, SignalingMessage msg) {
        String nick = userRepository.findById(fromUserId)
                .map(User::getNick)
                .orElse(fromUserId);
        msg.setFromUserId(fromUserId);
        msg.setFromNickname(nick);
        messagingTemplate.convertAndSendToUser(msg.getToUserId(), "/queue/voice", msg);
    }

    public void toggleMic(Long roomIdx, String userId, boolean micOn) {
        userRepository.findById(userId).ifPresent(user -> {
            SignalingMessage msg = new SignalingMessage();
            msg.setType("MIC_TOGGLE");
            msg.setFromUserId(userId);
            msg.setFromNickname(user.getNick());
            msg.setMicOn(micOn);
            messagingTemplate.convertAndSend("/topic/voice/" + roomIdx, msg);
        });
    }
}
