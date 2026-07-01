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

    /** 사용자가 기능/사용법을 물을 때 정확히 안내하도록 프롬프트에 붙이는 Collab 서비스 기능 요약. */
    private static final String SERVICE_GUIDE =
            "[Collab 서비스 기능 안내 — 사용자가 기능/사용법을 물으면 아래 내용을 바탕으로 정확히 안내해. " +
            "아래에 없는 내용은 모른다고 솔직히 말하고, 없는 기능을 지어내지 마.]\n" +
            "- 채팅: 실시간 메시지 송수신, 1:1 DM·단체 채팅방·팀 채널. 위로 스크롤하면 이전 메시지가 자동으로 로드되고, 날짜가 바뀌면 구분선이 표시돼.\n" +
            "- 파일: 입력창의 클립 아이콘이나 채팅 영역에 드래그앤드롭으로 첨부(최대 50MB). 파일 패널에서 목록·다운로드·삭제할 수 있고, 올린 사람은 메시지 옆 x로 삭제 가능해.\n" +
            "- 실시간 번역: 받은 메시지에 마우스를 올리면 나오는 번역 아이콘을 누르면 한국어로 번역돼(다시 누르면 숨김).\n" +
            "- 음성·화상 채팅: 채팅방 헤더 아이콘으로 입장(WebRTC). 마이크 켜고 끄기, 녹음이 가능하고, 녹음 파일은 서버에 30일 보관되어 파일 패널에서 다운로드·삭제할 수 있어.\n" +
            "- 그림판: 채팅방에서 펼치는 화이트보드로, 여러 명이 실시간으로 함께 그리고 PNG로 저장할 수 있어.\n" +
            "- 엑셀 데이터 시각화: 첨부 메뉴의 'AI 데이터 분석'으로 엑셀/CSV를 올리면 AI가 차트를 만들어줘. 만든 차트는 방 전체에 실시간으로 공유되고 이미지로 다운로드할 수 있어.\n" +
            "- AI 회의록: 채팅 내용이나 음성 파일을 바탕으로 회의록을 자동 작성하고 .md 파일로 내보낼 수 있어.\n" +
            "- 일정: 사이드바와 헤더 캘린더에서 일정을 확인하고, 추가·수정할 때 제목·시간·참여인원·내용·카카오맵 위치를 지정할 수 있어.\n" +
            "- 친구·팀: 친구 추가와 요청 수락/거절, 팀 생성·멤버 초대·권한(리더/매니저/멤버) 관리를 할 수 있어.\n" +
            "- 검색·알림: 헤더에서 친구·팀·채팅방을 검색하고, 친구 요청·팀 초대 알림을 받을 수 있어.\n" +
            "- 기능 테스트: 헤더의 '기능 테스트' 버튼을 누르면 내가 친구 요청을 보내고, 수락하면 지금처럼 대화하며 채팅 기능을 시험해 볼 수 있어.";

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

    /**
     * 시연 종료 시 해당 유저와 봇 사이의 관계를 정리한다(시연계정 전용 호출).
     * 친구관계(요청/수락, 양방향)를 모두 삭제하고, 봇 DM 방에서 유저를 나가게 처리한다.
     * 프론트 추적 누락(예: 챗봇 단계 전 조기 중단)·이전 시연 잔재까지 확실히 정리하기 위한 백엔드 백스톱.
     */
    @Transactional
    public void cleanupBotRelationship(String userId) {
        User bot = userRepository.findById(BOT_USER_ID).orElse(null);
        User user = userRepository.findById(userId).orElse(null);
        if (bot == null || user == null) return;

        friendRepository.deleteAll(friendRepository.findRelationships(user, bot));

        chatRoomRepository.findDmRoom(bot, user).ifPresent(room ->
                roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, user)
                        .ifPresent(m -> m.setExitAt(LocalDateTime.now())));
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

        // 답변 생성 동안 프론트에 '답변 생성 중…' 인디케이터를 띄우도록 타이핑 신호 전송(테스트봇 전용).
        broadcastBotTyping(room, bot);

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
        sb.append("\n\n").append(SERVICE_GUIDE);
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

    /**
     * '답변 생성 중' 인디케이터용 컨트롤 이벤트. 실제 메시지로 저장하지 않고 방 토픽으로만 전송한다.
     * 프론트는 BOT_TYPING 수신 시 인디케이터를 띄우고, 이어 도착하는 실제 메시지로 대체한다.
     */
    private void broadcastBotTyping(ChatRoom room, User bot) {
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .roomIdx(room.getRoomIdx())
                .userId(bot.getUserId())
                .nickname(bot.getNick())
                .avatarUrl(bot.getAvatarUrl())
                .content("")
                .msgType("BOT_TYPING")
                .sentAt(LocalDateTime.now())
                .build();
        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomIdx(), payload);
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

        // 방을 열어두지 않은 유저에게도 알림이 가도록 각 멤버 user-queue로 NEW_MESSAGE 푸시
        // (일반 메시지의 ChatService.notifyRoomMembers와 동일 — 봇 메시지엔 이게 빠져 알림이 안 오던 문제)
        notifyRoomMembers(room, bot, msg.getContent());
    }

    /** 봇이 보낸 메시지도 방 멤버(봇 제외)의 알림 큐로 NEW_MESSAGE를 보낸다. */
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
}
