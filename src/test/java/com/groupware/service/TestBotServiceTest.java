package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.Friend;
import com.groupware.domain.Message;
import com.groupware.domain.Team;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestBotServiceTest {

    @InjectMocks private TestBotService testBotService;
    @Mock private UserRepository userRepository;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private FriendRepository friendRepository;
    @Mock private GeminiService geminiService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private PasswordEncoder passwordEncoder;

    private static final String BOT = TestBotService.BOT_USER_ID;

    // ─── isBotId ───────────────────────────────────────────────────────────

    @Test
    void 봇_아이디_판별() {
        assertThat(testBotService.isBotId(BOT)).isTrue();
        assertThat(testBotService.isBotId("user@test.com")).isFalse();
    }

    // ─── ensureBotAccount ──────────────────────────────────────────────────

    @Test
    void 봇_계정_없으면_생성() {
        ReflectionTestUtils.setField(testBotService, "botPassword", "pw");
        given(userRepository.existsById(BOT)).willReturn(false);
        given(passwordEncoder.encode("pw")).willReturn("ENC");

        testBotService.ensureBotAccount();

        verify(userRepository).save(any(User.class));
    }

    @Test
    void 봇_계정_이미있으면_생성안함() {
        given(userRepository.existsById(BOT)).willReturn(true);

        testBotService.ensureBotAccount();

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void 봇_비밀번호_없으면_생성_건너뜀() {
        ReflectionTestUtils.setField(testBotService, "botPassword", "");
        given(userRepository.existsById(BOT)).willReturn(false);

        testBotService.ensureBotAccount();

        verify(userRepository, never()).save(any(User.class));
    }

    // ─── inviteUser ────────────────────────────────────────────────────────

    @Test
    void 봇_친구요청_신규생성_및_알림() {
        User bot = user(BOT, "테스트봇");
        User user = user("u1", "유저");
        given(userRepository.findById(BOT)).willReturn(Optional.of(bot));
        given(userRepository.findById("u1")).willReturn(Optional.of(user));
        given(friendRepository.findByUserAndFriend(bot, user)).willReturn(Optional.empty());
        given(friendRepository.existsRelationship(bot, user)).willReturn(false);
        given(friendRepository.save(any(Friend.class))).willAnswer(inv -> inv.getArgument(0));

        testBotService.inviteUser("u1");

        verify(friendRepository).save(any(Friend.class));
        verify(messagingTemplate).convertAndSendToUser(
                eq("u1"), eq("/queue/notifications"), any(NotificationPayload.class));
    }

    @Test
    void 봇_친구요청_이미_친구면_요청생략하고_메시지발송() {
        User bot = user(BOT, "테스트봇");
        User user = user("u1", "유저");
        Friend accepted = new Friend();
        accepted.setUser(bot);
        accepted.setFriend(user);
        accepted.setStatus("ACCEPTED");
        ChatRoom room = room(7L, null);
        given(userRepository.findById(BOT)).willReturn(Optional.of(bot));
        given(userRepository.findById("u1")).willReturn(Optional.of(user));
        given(friendRepository.findByUserAndFriend(bot, user)).willReturn(Optional.of(accepted));
        given(chatRoomRepository.findDmRoom(bot, user)).willReturn(Optional.of(room));
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));

        String result = testBotService.inviteUser("u1");

        // 친구 요청·알림은 생략, DM에 메시지만 발송
        verify(friendRepository, never()).save(any(Friend.class));
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
        verify(messageRepository).save(any(Message.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/room/7"), any(ChatMessagePayload.class));
        assertThat(result).contains("이미 친구");
    }

    @Test
    void 봇_자신을_초대하면_예외() {
        assertThatThrownBy(() -> testBotService.inviteUser(BOT))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CANNOT_ADD_SELF));
    }

    // ─── onFriendshipWithBotAccepted ───────────────────────────────────────

    @Test
    void 수락후_DM생성_및_안내메시지() {
        User bot = user(BOT, "테스트봇");
        User user = user("u1", "유저");
        given(userRepository.findById(BOT)).willReturn(Optional.of(bot));
        given(userRepository.findById("u1")).willReturn(Optional.of(user));
        given(chatRoomRepository.findDmRoom(bot, user)).willReturn(Optional.empty());
        given(chatRoomRepository.save(any(ChatRoom.class))).willAnswer(inv -> {
            ChatRoom r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "roomIdx", 5L);
            return r;
        });
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));

        testBotService.onFriendshipWithBotAccepted("u1");

        // 안내 메시지 저장 + 방으로 브로드캐스트
        verify(messageRepository).save(any(Message.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/room/5"), any(ChatMessagePayload.class));
    }

    // ─── maybeAutoReply ────────────────────────────────────────────────────

    @Test
    void 봇DM에_유저메시지오면_Gemini답장() {
        ChatRoom room = room(1L, null);
        User bot = user(BOT, "테스트봇");
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById(BOT)).willReturn(Optional.of(bot));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, bot)).willReturn(true);
        given(messageRepository.findMessagesBefore(eq(room), eq(10L), any(Pageable.class)))
                .willReturn(List.of());
        given(geminiService.generateContent(anyString())).willReturn("반가워요!");
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));

        testBotService.maybeAutoReply(1L, 10L, "u1", "유저", "안녕");

        verify(geminiService).generateContent(contains("안녕"));
        // BOT_TYPING(답변 생성 중) → 실제 답장 순서로 2번 브로드캐스트
        ArgumentCaptor<ChatMessagePayload> captor = ArgumentCaptor.forClass(ChatMessagePayload.class);
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/room/1"), captor.capture());
        List<ChatMessagePayload> sent = captor.getAllValues();
        assertThat(sent.get(0).getMsgType()).isEqualTo("BOT_TYPING");
        assertThat(sent.get(1).getContent()).isEqualTo("반가워요!");
    }

    @Test
    void Gemini프롬프트에_서비스_기능안내_포함() {
        ChatRoom room = room(1L, null);
        User bot = user(BOT, "테스트봇");
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById(BOT)).willReturn(Optional.of(bot));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, bot)).willReturn(true);
        given(messageRepository.findMessagesBefore(eq(room), eq(10L), any(Pageable.class)))
                .willReturn(List.of());
        given(geminiService.generateContent(anyString())).willReturn("응!");
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));

        testBotService.maybeAutoReply(1L, 10L, "u1", "유저", "번역 기능 어떻게 써?");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiService).generateContent(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        // 서비스 안내 블록 + 환각 방지 문구
        assertThat(prompt).contains("[Collab 서비스 기능 안내");
        assertThat(prompt).contains("없는 기능을 지어내지 마");
        // 주요 기능 키워드
        assertThat(prompt).contains("실시간 번역");
        assertThat(prompt).contains("엑셀 데이터 시각화");
        assertThat(prompt).contains("AI 회의록");
        assertThat(prompt).contains("음성·화상 채팅");
        // 현재 메시지(마지막 턴)도 포함
        assertThat(prompt).contains("유저: 번역 기능 어떻게 써?");
    }

    @Test
    void 봇자신의_메시지엔_답장안함() {
        testBotService.maybeAutoReply(1L, 10L, BOT, "테스트봇", "내 메시지");

        verify(geminiService, never()).generateContent(anyString());
    }

    @Test
    void 팀채널이면_답장안함() {
        ChatRoom teamRoom = room(1L, new Team());
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(teamRoom));

        testBotService.maybeAutoReply(1L, 10L, "u1", "유저", "안녕");

        verify(geminiService, never()).generateContent(anyString());
    }

    @Test
    void 봇이_없는_방이면_답장안함() {
        ChatRoom room = room(1L, null);
        User bot = user(BOT, "테스트봇");
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById(BOT)).willReturn(Optional.of(bot));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, bot)).willReturn(false);

        testBotService.maybeAutoReply(1L, 10L, "u1", "유저", "안녕");

        verify(geminiService, never()).generateContent(anyString());
    }

    @Test
    void Gemini실패시_폴백메시지_발송() {
        ChatRoom room = room(1L, null);
        User bot = user(BOT, "테스트봇");
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById(BOT)).willReturn(Optional.of(bot));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, bot)).willReturn(true);
        given(messageRepository.findMessagesBefore(eq(room), eq(10L), any(Pageable.class)))
                .willReturn(List.of());
        given(geminiService.generateContent(anyString()))
                .willThrow(new CustomException(ErrorCode.AI_GENERATION_FAILED));
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));

        testBotService.maybeAutoReply(1L, 10L, "u1", "유저", "안녕");

        // 폴백이라도 메시지를 저장·발송한다 (BOT_TYPING + 폴백 메시지로 2번 브로드캐스트)
        verify(messageRepository).save(any(Message.class));
        verify(messagingTemplate, times(2)).convertAndSend(startsWith("/topic/room/"), any(ChatMessagePayload.class));
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private User user(String id, String nick) {
        User u = new User();
        u.setUserId(id);
        u.setNick(nick);
        return u;
    }

    private ChatRoom room(Long idx, Team team) {
        ChatRoom r = new ChatRoom();
        ReflectionTestUtils.setField(r, "roomIdx", idx);
        r.setTeam(team);
        return r;
    }
}
