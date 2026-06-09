package com.groupware.service;

import com.groupware.domain.User;
import com.groupware.dto.voice.SignalingMessage;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
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
    private final RoomMembershipChecker membershipChecker;

    // roomIdx → 현재 음성 세션 참여자 userId Set (인메모리, 단일 인스턴스 기준)
    private final ConcurrentHashMap<Long, Set<String>> roomParticipants = new ConcurrentHashMap<>();

    public void join(Long roomIdx, String userId, String sessionType) {
        membershipChecker.check(roomIdx, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        roomParticipants.computeIfAbsent(roomIdx, k -> ConcurrentHashMap.newKeySet()).add(userId);

        SignalingMessage msg = new SignalingMessage();
        msg.setType("JOIN");
        msg.setFromUserId(userId);
        msg.setFromNickname(user.getNick());
        msg.setSessionType(sessionType != null ? sessionType : "VOICE");
        messagingTemplate.convertAndSend("/topic/voice/" + roomIdx, msg);
    }

    public void leave(Long roomIdx, String userId) {
        membershipChecker.check(roomIdx, userId);

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
        membershipChecker.check(roomIdx, fromUserId);
        String nick = userRepository.findById(fromUserId)
                .map(User::getNick)
                .orElse(fromUserId);
        msg.setFromUserId(fromUserId);
        msg.setFromNickname(nick);
        messagingTemplate.convertAndSendToUser(msg.getToUserId(), "/queue/voice", msg);
    }

    public void toggleMic(Long roomIdx, String userId, boolean micOn) {
        membershipChecker.check(roomIdx, userId);
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
