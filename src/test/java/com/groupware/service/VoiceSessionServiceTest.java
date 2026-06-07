package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.User;
import com.groupware.dto.voice.SignalingMessage;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoiceSessionServiceTest {

    @InjectMocks private VoiceSessionService service;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private UserRepository userRepository;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;

    private User userA;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        userA = new User();
        userA.setUserId("alice@test.com");
        userA.setNick("Alice");

        room = new ChatRoom();
        room.setRoomIdx(1L);
    }

    // ── join ──────────────────────────────────────────

    @Test
    void join_broadcasts_JOIN_message_to_topic() {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("alice@test.com")).willReturn(Optional.of(userA));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(true);

        service.join(1L, "alice@test.com", "VOICE");

        ArgumentCaptor<SignalingMessage> captor = ArgumentCaptor.forClass(SignalingMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/voice/1"), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("JOIN");
        assertThat(captor.getValue().getFromUserId()).isEqualTo("alice@test.com");
        assertThat(captor.getValue().getFromNickname()).isEqualTo("Alice");
        assertThat(captor.getValue().getSessionType()).isEqualTo("VOICE");
    }

    @Test
    void join_defaults_sessionType_to_VOICE_when_null() {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("alice@test.com")).willReturn(Optional.of(userA));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(true);

        service.join(1L, "alice@test.com", null);

        ArgumentCaptor<SignalingMessage> captor = ArgumentCaptor.forClass(SignalingMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/voice/1"), captor.capture());
        assertThat(captor.getValue().getSessionType()).isEqualTo("VOICE");
    }

    @Test
    void join_throws_CHAT_ROOM_NOT_FOUND_when_room_missing() {
        given(chatRoomRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(99L, "alice@test.com", "VOICE"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void join_throws_NOT_ROOM_MEMBER_when_not_member() {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("alice@test.com")).willReturn(Optional.of(userA));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(false);

        assertThatThrownBy(() -> service.join(1L, "alice@test.com", "VOICE"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_ROOM_MEMBER);
    }

    // ── leave ─────────────────────────────────────────

    @Test
    void leave_broadcasts_LEAVE_message_to_topic() {
        given(userRepository.findById("alice@test.com")).willReturn(Optional.of(userA));

        service.leave(1L, "alice@test.com");

        ArgumentCaptor<SignalingMessage> captor = ArgumentCaptor.forClass(SignalingMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/voice/1"), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("LEAVE");
        assertThat(captor.getValue().getFromUserId()).isEqualTo("alice@test.com");
    }

    @Test
    void leave_does_not_broadcast_when_user_not_found() {
        given(userRepository.findById("ghost@test.com")).willReturn(Optional.empty());

        service.leave(1L, "ghost@test.com");

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    // ── signal ────────────────────────────────────────

    @Test
    void signal_routes_OFFER_to_target_user_queue() {
        given(userRepository.findById("alice@test.com")).willReturn(Optional.of(userA));
        SignalingMessage msg = new SignalingMessage();
        msg.setType("OFFER");
        msg.setToUserId("bob@test.com");
        msg.setSdp("v=0...");

        service.signal(1L, "alice@test.com", msg);

        ArgumentCaptor<SignalingMessage> captor = ArgumentCaptor.forClass(SignalingMessage.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("bob@test.com"), eq("/queue/voice"), captor.capture());
        assertThat(captor.getValue().getFromUserId()).isEqualTo("alice@test.com");
        assertThat(captor.getValue().getFromNickname()).isEqualTo("Alice");
        assertThat(captor.getValue().getType()).isEqualTo("OFFER");
    }

    // ── toggleMic ─────────────────────────────────────

    @Test
    void toggleMic_broadcasts_MIC_TOGGLE_with_micOn_false() {
        given(userRepository.findById("alice@test.com")).willReturn(Optional.of(userA));

        service.toggleMic(1L, "alice@test.com", false);

        ArgumentCaptor<SignalingMessage> captor = ArgumentCaptor.forClass(SignalingMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/voice/1"), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("MIC_TOGGLE");
        assertThat(captor.getValue().getMicOn()).isFalse();
    }

    @Test
    void toggleMic_broadcasts_MIC_TOGGLE_with_micOn_true() {
        given(userRepository.findById("alice@test.com")).willReturn(Optional.of(userA));

        service.toggleMic(1L, "alice@test.com", true);

        ArgumentCaptor<SignalingMessage> captor = ArgumentCaptor.forClass(SignalingMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/voice/1"), captor.capture());
        assertThat(captor.getValue().getMicOn()).isTrue();
    }
}
