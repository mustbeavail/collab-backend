package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.Friend;
import com.groupware.domain.Message;
import com.groupware.domain.RoomMember;
import com.groupware.domain.User;
import com.groupware.dto.chat.ChatMessagePayload;
import com.groupware.dto.notification.NotificationPayload;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.FriendRepository;
import com.groupware.repository.MessageRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 채팅 테스트용 AI 봇('테스트봇').
 * - 시동 시 봇 계정 멱등 생성
 * - 기능 테스트 버튼 → 봇이 유저에게 친구 요청
 * - 유저 수락 → 봇이 DM 방을 만들고 안내 메시지 발송
 * - 봇과의 DM에 유저가 메시지를 보내면 Gemini로 답장(@Async)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestBotService {

    public static final String BOT_USER_ID = "testbot@naver.com";
    public static final String BOT_NICK = "테스트봇";

    /** Gemini 답장 시 함께 보낼 직전 대화 개수 */
    private static final int CONTEXT_MESSAGE_LIMIT = 20;

    private static final String WELCOME_MESSAGE =
            "안녕하세요! 저는 채팅 기능 테스트용 AI 봇 '테스트봇'이에요. 🤖\n" +
            "이 창에 아무 말이나 입력하면 제가 답장해 드려요. 메시지 전송, 번역, 스크롤 등 채팅 기능을 자유롭게 테스트해 보세요!";

    private static final String REENTRY_MESSAGE =
            "다시 오셨네요! 😄 저는 계속 여기 있어요. 편하게 메시지를 보내며 채팅 기능을 테스트해 보세요!";

    private static final String CHAT_SYSTEM_PROMPT =
            "너는 'Collab' 협업 메신저의 채팅 테스트용 AI 챗봇 '테스트봇'이야. " +
            "사용자와 한국어로 자유롭게 대화해. 답변은 너무 길지 않게(2~4문장) 친근하고 자연스럽게 해. " +
            "마크다운 머리말이나 불필요한 접두어 없이 답변 본문만 출력해.";

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MessageRepository messageRepository;
    private final FriendRepository friendRepository;
    private final GeminiService geminiService;
    private final SimpMessagingTemplate messagingTemplate;
    private final PasswordEncoder passwordEncoder;

    @Value("${testbot.password:}")
    private String botPassword;

    public boolean isBotId(String userId) {
        return BOT_USER_ID.equals(userId);
    }

    // ─── 봇 계정 시동 시 생성 ──────────────────────────────────────────────

    @Transactional
    public void ensureBotAccount() {
        if (userRepository.existsById(BOT_USER_ID)) {
            return;
        }
        if (!StringUtils.hasText(botPassword)) {
            log.warn("testbot.password 미설정 → 테스트봇 계정 생성을 건너뜀 (application-local.yml에 testbot.password 추가 필요)");
            return;
        }
        User bot = new User();
        bot.setUserId(BOT_USER_ID);
        bot.setPw(passwordEncoder.encode(botPassword));
        bot.setNick(BOT_NICK);
        bot.setAbout("채팅 기능 테스트용 AI 봇입니다.");
        bot.setJoinAt(LocalDateTime.now());
        userRepository.save(bot);
        log.info("테스트봇 계정 생성 완료: {}", BOT_USER_ID);
    }

    // ─── 친구 초대 (버튼 클릭) ─────────────────────────────────────────────

    /**
     * 봇이 해당 유저에게 친구 요청을 보낸다.
     * - 이미 친구면 친구 요청을 생략하고 봇 DM에 메시지만 보낸다.
     * - 보류 중이면 알림만 재전송한다.
     * @return 프론트에 보여줄 결과 메시지
     */
    @Transactional
    public String inviteUser(String userId) {
        if (isBotId(userId)) {
            throw new CustomException(ErrorCode.CANNOT_ADD_SELF);
        }
        User bot = getBot();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Friend request;
        Optional<Friend> existing = friendRepository.findByUserAndFriend(bot, user);
        if (existing.isPresent()) {
            request = existing.get();
            if ("ACCEPTED".equals(request.getStatus())) {
                // 이미 친구 → 친구 요청 생략, 봇 DM에 메시지만 발송
                ChatRoom room = getOrCreateDm(bot, user);
                saveAndBroadcast(room, bot, REENTRY_MESSAGE);
                return "테스트봇과 이미 친구예요. 채팅방에 메시지를 보냈어요!";
            }
        } else if (friendRepository.existsRelationship(bot, user)) {
            return "이미 친구 요청이 진행 중이에요.";
        } else {
            request = new Friend();
            request.setUser(bot);
            request.setFriend(user);
            request.setStatus("PENDING");
            request = friendRepository.save(request);
        }

        messagingTemplate.convertAndSendToUser(
                user.getUserId(),
                "/queue/notifications",
                NotificationPayload.builder()
                        .type("FRIEND_REQUEST")
                        .friendIdx(request.getFriendIdx())
                        .userId(bot.getUserId())
                        .nickname(bot.getNick())
                        .email(bot.getUserId())
                        .status("PENDING")
                        .build()
        );
        return "테스트봇이 친구 요청을 보냈어요!";
    }

    // ─── 수락 후 안내 메시지 ───────────────────────────────────────────────

    /**
     * 유저가 봇의 친구 요청을 수락한 직후 호출 → DM 방을 만들고 안내 메시지를 보낸다.
     */
    @Transactional
    public void onFriendshipWithBotAccepted(String userId) {
        User bot = getBot();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ChatRoom room = getOrCreateDm(bot, user);
        saveAndBroadcast(room, bot, WELCOME_MESSAGE);
    }

    // ─── 자동 답장 ─────────────────────────────────────────────────────────

    /**
     * 유저가 봇과의 DM에 메시지를 보내면 호출(@Async). Gemini로 답장 생성 후 발송.
     * 별도 트랜잭션/스레드에서 실행되므로 방·발신자 식별값만 받아 재조회한다.
     */
    @Async
    @Transactional
    public void maybeAutoReply(Long roomIdx, Long triggerMsgIdx, String senderId, String senderNick, String content) {
        if (isBotId(senderId)) {
            return; // 봇 자신의 메시지에는 응답하지 않음
        }
        ChatRoom room = chatRoomRepository.findById(roomIdx)
                .filter(r -> r.getDelDate() == null)
                .orElse(null);
        if (room == null || room.getTeam() != null) {
            return; // 팀 채널 아님(=DM)만 대상
        }
        User bot = userRepository.findById(BOT_USER_ID).orElse(null);
        if (bot == null || !roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, bot)) {
            return; // 봇이 참여한 방이 아니면 무시
        }

        try {
            String reply = geminiService.generateContent(buildPrompt(room, triggerMsgIdx, senderNick, content));
            if (StringUtils.hasText(reply)) {
                saveAndBroadcast(room, bot, reply.trim());
            }
        } catch (Exception e) {
            log.error("테스트봇 답장 생성 실패 roomIdx={}: {}", roomIdx, e.getMessage());
            saveAndBroadcast(room, bot, "(앗, 지금은 답장을 만들 수 없어요. 잠시 후 다시 시도해 주세요.)");
        }
    }

    private String buildPrompt(ChatRoom room, Long triggerMsgIdx, String senderNick, String content) {
        // triggerMsgIdx 이전 메시지들을 문맥으로, 현재 메시지를 마지막 턴으로 붙인다.
        List<Message> history = messageRepository.findMessagesBefore(
                room, triggerMsgIdx, PageRequest.of(0, CONTEXT_MESSAGE_LIMIT));

        StringBuilder sb = new StringBuilder(CHAT_SYSTEM_PROMPT);
        sb.append("\n\n[최근 대화]\n");
        List<Message> chronological = new ArrayList<>(history);
        java.util.Collections.reverse(chronological); // DESC → 오래된 순
        for (Message m : chronological) {
            String nick = m.getUser() != null ? m.getUser().getNick() : "유저";
            sb.append(nick).append(": ").append(m.getContent()).append("\n");
        }
        sb.append(senderNick).append(": ").append(content).append("\n");
        sb.append("\n위 대화에 이어 '").append(BOT_NICK).append("'으로서 할 다음 답변만 출력해.");
        return sb.toString();
    }

    // ─── 내부 헬퍼 ─────────────────────────────────────────────────────────

    private User getBot() {
        return userRepository.findById(BOT_USER_ID)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private ChatRoom getOrCreateDm(User bot, User user) {
        return chatRoomRepository.findDmRoom(bot, user)
                .orElseGet(() -> createDm(bot, user));
    }

    private ChatRoom createDm(User bot, User user) {
        ChatRoom room = new ChatRoom();
        room.setRoomName(bot.getNick() + ", " + user.getNick());
        room.setCreatedAt(LocalDateTime.now());
        room = chatRoomRepository.save(room);

        RoomMember m1 = new RoomMember();
        m1.setChatRoom(room);
        m1.setUser(user);
        m1.setRole("OWNER");
        m1.setJoinAt(LocalDateTime.now());
        roomMemberRepository.save(m1);

        RoomMember m2 = new RoomMember();
        m2.setChatRoom(room);
        m2.setUser(bot);
        m2.setRole("MEMBER");
        m2.setJoinAt(LocalDateTime.now());
        roomMemberRepository.save(m2);

        return room;
    }

    private void saveAndBroadcast(ChatRoom room, User bot, String content) {
        Message msg = new Message();
        msg.setChatRoom(room);
        msg.setUser(bot);
        msg.setContent(content);
        msg.setMsgType("TEXT");
        msg.setSentAt(LocalDateTime.now());
        msg.setDelYn(false);
        msg = messageRepository.save(msg);

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .msgIdx(msg.getMsgIdx())
                .roomIdx(room.getRoomIdx())
                .userId(bot.getUserId())
                .nickname(bot.getNick())
                .avatarUrl(bot.getAvatarUrl())
                .content(msg.getContent())
                .msgType(msg.getMsgType())
                .sentAt(msg.getSentAt())
                .build();
        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomIdx(), payload);
    }
}
