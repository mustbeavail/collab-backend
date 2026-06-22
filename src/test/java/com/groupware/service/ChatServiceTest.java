package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.Message;
import com.groupware.domain.RoomMember;
import com.groupware.domain.User;
import com.groupware.dto.chat.ChatMessagePayload;
import com.groupware.dto.chat.ChatRoomDetailResponse;
import com.groupware.dto.chat.ChatRoomResponse;
import com.groupware.dto.chat.DmRoomResponse;
import com.groupware.dto.chat.InviteRequest;
import com.groupware.dto.notification.NotificationPayload;
import com.groupware.domain.Team;
import com.groupware.dto.chat.MessagePageResponse;
import com.groupware.dto.chat.UpdateRoomInfoRequest;
import com.groupware.dto.chat.RoomMemberResponse;
import com.groupware.dto.chat.SendMessageRequest;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.MessageRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.TeamMemberRepository;
import com.groupware.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @InjectMocks private ChatService chatService;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private TestBotService testBotService;

    private User userA;
    private User userB;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        userA = new User();
        ReflectionTestUtils.setField(userA, "userId", "a@test.com");
        ReflectionTestUtils.setField(userA, "nick", "Alice");

        userB = new User();
        ReflectionTestUtils.setField(userB, "userId", "b@test.com");
        ReflectionTestUtils.setField(userB, "nick", "Bob");

        room = new ChatRoom();
        ReflectionTestUtils.setField(room, "roomIdx", 1L);
        ReflectionTestUtils.setField(room, "roomName", "general");
    }

    private Message makeMsg(long idx, String content) {
        Message msg = new Message();
        ReflectionTestUtils.setField(msg, "msgIdx", idx);
        ReflectionTestUtils.setField(msg, "user", userA);
        ReflectionTestUtils.setField(msg, "chatRoom", room);
        ReflectionTestUtils.setField(msg, "content", content);
        ReflectionTestUtils.setField(msg, "msgType", "TEXT");
        ReflectionTestUtils.setField(msg, "sentAt", LocalDateTime.now());
        ReflectionTestUtils.setField(msg, "delYn", false);
        return msg;
    }

    @Test
    void 최근_메시지_조회_성공_hasMore_false() {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(true);
        given(messageRepository.findLatestMessages(eq(room), any(Pageable.class)))
                .willReturn(List.of(makeMsg(2L, "b"), makeMsg(1L, "a")));

        MessagePageResponse result = chatService.getMessages("a@test.com", 1L, null, 50);

        assertThat(result.getMessages()).hasSize(2);
        assertThat(result.getMessages().get(0).getContent()).isEqualTo("a");
        assertThat(result.isHasMore()).isFalse();
    }

    @Test
    void 최근_메시지_조회_hasMore_true() {
        List<Message> fetched = new ArrayList<>();
        for (int i = 51; i >= 1; i--) fetched.add(makeMsg(i, "msg" + i));

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(true);
        given(messageRepository.findLatestMessages(eq(room), any(Pageable.class))).willReturn(fetched);

        MessagePageResponse result = chatService.getMessages("a@test.com", 1L, null, 50);

        assertThat(result.getMessages()).hasSize(50);
        assertThat(result.isHasMore()).isTrue();
    }

    @Test
    void before_커서_기반_조회() {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(true);
        given(messageRepository.findMessagesBefore(eq(room), eq(10L), any(Pageable.class)))
                .willReturn(List.of(makeMsg(9L, "old"), makeMsg(8L, "older")));

        MessagePageResponse result = chatService.getMessages("a@test.com", 1L, 10L, 50);

        assertThat(result.getMessages()).hasSize(2);
        assertThat(result.getMessages().get(0).getMsgIdx()).isEqualTo(8L);
        assertThat(result.isHasMore()).isFalse();
    }

    @Test
    void 최근_메시지_조회_방없음_예외() {
        given(chatRoomRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getMessages("a@test.com", 99L, null, 50))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void 최근_메시지_조회_멤버아님_예외() {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(false);

        assertThatThrownBy(() -> chatService.getMessages("a@test.com", 1L, null, 50))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_ROOM_MEMBER);
    }

    @Test
    void 메시지_전송_성공_WebSocket_브로드캐스트() {
        SendMessageRequest req = new SendMessageRequest();
        ReflectionTestUtils.setField(req, "content", "hi");
        ReflectionTestUtils.setField(req, "msgType", "TEXT");

        Message saved = makeMsg(10L, "hi");

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(true);
        given(messageRepository.save(any(Message.class))).willReturn(saved);

        chatService.sendMessage("a@test.com", 1L, req);

        ArgumentCaptor<ChatMessagePayload> captor = ArgumentCaptor.forClass(ChatMessagePayload.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/room/1"), captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("hi");
        assertThat(captor.getValue().getRoomIdx()).isEqualTo(1L);
    }

    @Test
    void 메시지_전송_시_다른_멤버에게_NEW_MESSAGE_알림() {
        SendMessageRequest req = new SendMessageRequest();
        ReflectionTestUtils.setField(req, "content", "hi");
        ReflectionTestUtils.setField(req, "msgType", "TEXT");

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(true);
        given(messageRepository.save(any(Message.class))).willReturn(makeMsg(10L, "hi"));
        given(roomMemberRepository.findAllActiveByRoom(room)).willReturn(List.of(roomMember(userA), roomMember(userB)));

        chatService.sendMessage("a@test.com", 1L, req);

        // 발신자 제외 다른 멤버(b)에게만 NEW_MESSAGE 푸시
        verify(messagingTemplate).convertAndSendToUser(eq("b@test.com"), eq("/queue/notifications"), any(NotificationPayload.class));
        verify(messagingTemplate, never()).convertAndSendToUser(eq("a@test.com"), anyString(), any());
    }

    @Test
    void 내_DM방_목록_DM과_그룹_구분() {
        User userC = new User();
        ReflectionTestUtils.setField(userC, "userId", "c@test.com");
        ReflectionTestUtils.setField(userC, "nick", "Carol");

        ChatRoom group = new ChatRoom();
        ReflectionTestUtils.setField(group, "roomIdx", 2L);
        ReflectionTestUtils.setField(group, "roomName", "그룹방");

        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(chatRoomRepository.findMyDmRooms("a@test.com")).willReturn(List.of(room, group));
        given(roomMemberRepository.findAllActiveByRoom(room)).willReturn(List.of(roomMember(userA), roomMember(userB)));
        given(roomMemberRepository.findAllActiveByRoom(group)).willReturn(List.of(roomMember(userA), roomMember(userB), roomMember(userC)));

        List<DmRoomResponse> result = chatService.getMyDmRooms("a@test.com");

        DmRoomResponse dm = result.stream().filter(r -> r.getRoomIdx() == 1L).findFirst().orElseThrow();
        assertThat(dm.isDm()).isTrue();
        assertThat(dm.getName()).isEqualTo("Bob");
        assertThat(dm.getTargetUserId()).isEqualTo("b@test.com");

        DmRoomResponse g = result.stream().filter(r -> r.getRoomIdx() == 2L).findFirst().orElseThrow();
        assertThat(g.isDm()).isFalse();
        assertThat(g.getName()).isEqualTo("그룹방");
    }

    @Test
    void 이름변경한_2인DM은_상대닉네임_대신_커스텀이름_표시() {
        // I-15: custom_name=true인 2인 DM은 상대 닉네임이 아니라 직접 지정한 방 이름을 표시한다.
        ChatRoom custom = new ChatRoom();
        ReflectionTestUtils.setField(custom, "roomIdx", 3L);
        ReflectionTestUtils.setField(custom, "roomName", "내 커스텀 방제");
        ReflectionTestUtils.setField(custom, "customName", true);

        RoomMember ownerA = roomMember(userA);
        ReflectionTestUtils.setField(ownerA, "role", "OWNER");

        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(chatRoomRepository.findMyDmRooms("a@test.com")).willReturn(List.of(custom));
        given(roomMemberRepository.findAllActiveByRoom(custom)).willReturn(List.of(ownerA, roomMember(userB)));

        DmRoomResponse dm = chatService.getMyDmRooms("a@test.com").get(0);

        assertThat(dm.isDm()).isTrue();
        assertThat(dm.getName()).isEqualTo("내 커스텀 방제"); // 상대 닉네임("Bob")이 아님
        assertThat(dm.isOwner()).isTrue();                    // 이름변경 권한(OWNER)
        assertThat(dm.getMemberCount()).isEqualTo(2);
    }

    @Test
    void 이름변경_안한_2인DM은_상대_닉네임_표시() {
        // I-15: custom_name이 아니면(기본) 종전대로 상대방 현재 닉네임을 표시한다.
        ReflectionTestUtils.setField(room, "roomName", "Alice, Bob"); // 생성시 자동 이름(표시에 안 씀)

        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(chatRoomRepository.findMyDmRooms("a@test.com")).willReturn(List.of(room));
        given(roomMemberRepository.findAllActiveByRoom(room)).willReturn(List.of(roomMember(userA), roomMember(userB)));

        DmRoomResponse dm = chatService.getMyDmRooms("a@test.com").get(0);

        assertThat(dm.getName()).isEqualTo("Bob"); // 자동 이름이 아니라 상대 닉네임
    }

    @Test
    void 팀채널_초대_팀멤버_성공() {
        Team team = new Team();
        ReflectionTestUtils.setField(team, "teamIdx", 7L);
        ChatRoom teamRoom = new ChatRoom();
        ReflectionTestUtils.setField(teamRoom, "roomIdx", 9L);
        ReflectionTestUtils.setField(teamRoom, "team", team);

        InviteRequest req = new InviteRequest();
        ReflectionTestUtils.setField(req, "userId", "b@test.com");

        given(chatRoomRepository.findById(9L)).willReturn(Optional.of(teamRoom));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(userRepository.findById("b@test.com")).willReturn(Optional.of(userB));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(7L, "a@test.com")).willReturn(Optional.of(new com.groupware.domain.TeamMember()));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(7L, "b@test.com")).willReturn(Optional.of(new com.groupware.domain.TeamMember()));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(teamRoom, userB)).willReturn(false);
        given(roomMemberRepository.save(any(RoomMember.class))).willAnswer(inv -> inv.getArgument(0));
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));

        chatService.inviteToRoom("a@test.com", 9L, req);

        verify(roomMemberRepository).save(any(RoomMember.class));
    }

    private RoomMember roomMember(User u) {
        RoomMember rm = new RoomMember();
        ReflectionTestUtils.setField(rm, "user", u);
        ReflectionTestUtils.setField(rm, "role", "MEMBER");
        return rm;
    }

    @Test
    void DM방_없으면_새로_생성() {
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(userRepository.findById("b@test.com")).willReturn(Optional.of(userB));
        // 카카오톡식(I-2): 정확히 2명 일치하는 방이 없으면 새로 만든다
        given(chatRoomRepository.findDmRoom(userA, userB)).willReturn(Optional.empty());

        ChatRoom newRoom = new ChatRoom();
        ReflectionTestUtils.setField(newRoom, "roomIdx", 5L);
        ReflectionTestUtils.setField(newRoom, "roomName", "Alice, Bob");
        given(chatRoomRepository.save(any(ChatRoom.class))).willReturn(newRoom);
        given(roomMemberRepository.save(any(RoomMember.class))).willAnswer(inv -> inv.getArgument(0));

        ChatRoomResponse resp = chatService.getOrCreateDmRoom("a@test.com", "b@test.com");

        assertThat(resp.getRoomIdx()).isEqualTo(5L);
    }

    @Test
    void 멤버_목록_조회_성공() {
        RoomMember rm = new RoomMember();
        ReflectionTestUtils.setField(rm, "user", userA);
        ReflectionTestUtils.setField(rm, "role", "OWNER");

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(true);
        given(roomMemberRepository.findAllActiveByRoom(room)).willReturn(List.of(rm));

        List<RoomMemberResponse> result = chatService.getRoomMembers("a@test.com", 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRole()).isEqualTo("OWNER");
    }

    @Test
    void 초대_성공() {
        InviteRequest req = new InviteRequest();
        ReflectionTestUtils.setField(req, "userId", "b@test.com");

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(userRepository.findById("b@test.com")).willReturn(Optional.of(userB));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(true);
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, userB)).willReturn(false);
        given(roomMemberRepository.save(any(RoomMember.class))).willAnswer(inv -> inv.getArgument(0));
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));

        chatService.inviteToRoom("a@test.com", 1L, req);

        verify(roomMemberRepository).save(any(RoomMember.class));
    }

    @Test
    void 팀채팅방_초대_일반멤버는_권한없음() {
        com.groupware.domain.Team team = new com.groupware.domain.Team();
        ReflectionTestUtils.setField(team, "teamIdx", 7L);
        ChatRoom teamRoom = new ChatRoom();
        ReflectionTestUtils.setField(teamRoom, "roomIdx", 2L);
        ReflectionTestUtils.setField(teamRoom, "team", team);

        InviteRequest req = new InviteRequest();
        ReflectionTestUtils.setField(req, "userId", "b@test.com");

        given(chatRoomRepository.findById(2L)).willReturn(Optional.of(teamRoom));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(userRepository.findById("b@test.com")).willReturn(Optional.of(userB));
        com.groupware.domain.TeamMember inviter = new com.groupware.domain.TeamMember();
        ReflectionTestUtils.setField(inviter, "role", "MEMBER");
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(7L, "a@test.com")).willReturn(Optional.of(inviter));

        // 팀 채팅방 초대는 권한자(LEADER/MANAGER)만(버그 항목17)
        assertThatThrownBy(() -> chatService.inviteToRoom("a@test.com", 2L, req))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TEAM_ACCESS_DENIED);
    }

    @Test
    void 팀채팅방_삭제_리더_성공() {
        com.groupware.domain.Team team = new com.groupware.domain.Team();
        ReflectionTestUtils.setField(team, "teamIdx", 7L);
        ChatRoom teamRoom = new ChatRoom();
        ReflectionTestUtils.setField(teamRoom, "roomIdx", 2L);
        ReflectionTestUtils.setField(teamRoom, "team", team);

        given(chatRoomRepository.findById(2L)).willReturn(Optional.of(teamRoom));
        com.groupware.domain.TeamMember leader = new com.groupware.domain.TeamMember();
        ReflectionTestUtils.setField(leader, "role", "LEADER");
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(7L, "a@test.com")).willReturn(Optional.of(leader));
        given(chatRoomRepository.save(any(ChatRoom.class))).willAnswer(inv -> inv.getArgument(0));

        chatService.deleteRoom("a@test.com", 2L);

        assertThat(teamRoom.getDelDate()).isNotNull();
    }

    @Test
    void 팀채팅방_삭제_리더아니면_예외() {
        com.groupware.domain.Team team = new com.groupware.domain.Team();
        ReflectionTestUtils.setField(team, "teamIdx", 7L);
        ChatRoom teamRoom = new ChatRoom();
        ReflectionTestUtils.setField(teamRoom, "roomIdx", 2L);
        ReflectionTestUtils.setField(teamRoom, "team", team);

        given(chatRoomRepository.findById(2L)).willReturn(Optional.of(teamRoom));
        com.groupware.domain.TeamMember mgr = new com.groupware.domain.TeamMember();
        ReflectionTestUtils.setField(mgr, "role", "MANAGER");
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(7L, "a@test.com")).willReturn(Optional.of(mgr));

        assertThatThrownBy(() -> chatService.deleteRoom("a@test.com", 2L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TEAM_ACCESS_DENIED);
    }

    @Test
    void 일반채팅방_삭제_불가() {
        // 팀이 없는 방은 삭제 대상 아님(나가기로 처리)
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> chatService.deleteRoom("a@test.com", 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void 나가기_성공() {
        RoomMember rm = new RoomMember();
        ReflectionTestUtils.setField(rm, "user", userA);
        ReflectionTestUtils.setField(rm, "role", "MEMBER");

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(Optional.of(rm));
        given(roomMemberRepository.save(any(RoomMember.class))).willAnswer(inv -> inv.getArgument(0));
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));

        chatService.leaveRoom("a@test.com", 1L);

        assertThat(rm.getExitAt()).isNotNull();
    }

    @Test
    void 추방_성공_방장만() {
        RoomMember ownerMember = new RoomMember();
        ReflectionTestUtils.setField(ownerMember, "user", userA);
        ReflectionTestUtils.setField(ownerMember, "role", "OWNER");

        RoomMember targetMember = new RoomMember();
        ReflectionTestUtils.setField(targetMember, "user", userB);
        ReflectionTestUtils.setField(targetMember, "role", "MEMBER");

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(userRepository.findById("b@test.com")).willReturn(Optional.of(userB));
        given(roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(Optional.of(ownerMember));
        given(roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, userB)).willReturn(Optional.of(targetMember));
        given(roomMemberRepository.save(any(RoomMember.class))).willAnswer(inv -> inv.getArgument(0));

        chatService.kickMember("a@test.com", 1L, "b@test.com");

        assertThat(targetMember.getExitAt()).isNotNull();
    }

    @Test
    void 추방_방장아니면_예외() {
        RoomMember memberRole = new RoomMember();
        ReflectionTestUtils.setField(memberRole, "user", userA);
        ReflectionTestUtils.setField(memberRole, "role", "MEMBER");

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(Optional.of(memberRole));

        assertThatThrownBy(() -> chatService.kickMember("a@test.com", 1L, "b@test.com"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TEAM_ACCESS_DENIED);
    }

    @Test
    void 방정보_조회_성공() {
        RoomMember myMember = new RoomMember();
        ReflectionTestUtils.setField(myMember, "role", "OWNER");

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(true);
        given(roomMemberRepository.findAllActiveByRoom(room)).willReturn(List.of());
        given(roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(Optional.of(myMember));

        ChatRoomDetailResponse result = chatService.getRoomInfo("a@test.com", 1L);

        assertThat(result.getRoomName()).isEqualTo("general");
        assertThat(result.isDm()).isTrue();
        assertThat(result.getMyRole()).isEqualTo("OWNER");
    }

    @Test
    void 방정보_수정_성공_방장만() {
        RoomMember ownerMember = new RoomMember();
        ReflectionTestUtils.setField(ownerMember, "user", userA);
        ReflectionTestUtils.setField(ownerMember, "role", "OWNER");

        UpdateRoomInfoRequest req = new UpdateRoomInfoRequest();
        ReflectionTestUtils.setField(req, "roomName", "새이름");

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(Optional.of(ownerMember));
        given(chatRoomRepository.save(any(ChatRoom.class))).willAnswer(inv -> inv.getArgument(0));
        given(roomMemberRepository.findAllActiveByRoom(room)).willReturn(List.of());

        ChatRoomDetailResponse result = chatService.updateRoomInfo("a@test.com", 1L, req);

        assertThat(result.getRoomName()).isEqualTo("새이름");
    }

    @Test
    void 방정보_수정_방장아님_예외() {
        RoomMember memberRole = new RoomMember();
        ReflectionTestUtils.setField(memberRole, "user", userA);
        ReflectionTestUtils.setField(memberRole, "role", "MEMBER");

        UpdateRoomInfoRequest req = new UpdateRoomInfoRequest();
        ReflectionTestUtils.setField(req, "roomName", "새이름");

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(Optional.of(memberRole));

        assertThatThrownBy(() -> chatService.updateRoomInfo("a@test.com", 1L, req))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TEAM_ACCESS_DENIED);
    }

    @Test
    void 방정보_수정_팀채널_비OWNER_예외() {
        // I-13: 팀 채팅방도 이름변경 가능하나 방 OWNER만. 비OWNER(MEMBER)는 차단.
        com.groupware.domain.Team team = new com.groupware.domain.Team();
        ReflectionTestUtils.setField(room, "team", team);

        RoomMember memberRole = new RoomMember();
        ReflectionTestUtils.setField(memberRole, "user", userA);
        ReflectionTestUtils.setField(memberRole, "role", "MEMBER");

        UpdateRoomInfoRequest req = new UpdateRoomInfoRequest();
        ReflectionTestUtils.setField(req, "roomName", "새이름");

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(Optional.of(memberRole));

        assertThatThrownBy(() -> chatService.updateRoomInfo("a@test.com", 1L, req))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TEAM_ACCESS_DENIED);
    }

    @Test
    void 방정보_수정_팀채널_OWNER_성공_및_브로드캐스트() {
        // I-13: 팀 채팅방 OWNER는 이름변경 가능 + 멤버에게 ROOM_RENAMED 실시간 전파
        com.groupware.domain.Team team = new com.groupware.domain.Team();
        ReflectionTestUtils.setField(room, "team", team);

        RoomMember ownerMember = new RoomMember();
        ReflectionTestUtils.setField(ownerMember, "user", userA);
        ReflectionTestUtils.setField(ownerMember, "role", "OWNER");

        UpdateRoomInfoRequest req = new UpdateRoomInfoRequest();
        ReflectionTestUtils.setField(req, "roomName", "변경된팀방");

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(Optional.of(ownerMember));
        given(chatRoomRepository.save(any(ChatRoom.class))).willAnswer(inv -> inv.getArgument(0));
        given(roomMemberRepository.findAllActiveByRoom(room)).willReturn(List.of(roomMember(userA), roomMember(userB)));

        ChatRoomDetailResponse result = chatService.updateRoomInfo("a@test.com", 1L, req);

        assertThat(result.getRoomName()).isEqualTo("변경된팀방");
        // 멤버 2명에게 ROOM_RENAMED 전파
        verify(messagingTemplate).convertAndSendToUser(eq("a@test.com"), eq("/queue/notifications"), any(NotificationPayload.class));
        verify(messagingTemplate).convertAndSendToUser(eq("b@test.com"), eq("/queue/notifications"), any(NotificationPayload.class));
    }

    @Test
    void DM방_이미_존재하면_기존_반환() {
        ChatRoom existing = new ChatRoom();
        ReflectionTestUtils.setField(existing, "roomIdx", 3L);
        ReflectionTestUtils.setField(existing, "roomName", "Alice, Bob");

        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(userRepository.findById("b@test.com")).willReturn(Optional.of(userB));
        // 정확히 2명 일치하는 방이 있으면 재사용(중복 생성 안 함, 버그 항목27 + I-2)
        given(chatRoomRepository.findDmRoom(userA, userB)).willReturn(Optional.of(existing));

        ChatRoomResponse resp = chatService.getOrCreateDmRoom("a@test.com", "b@test.com");

        assertThat(resp.getRoomIdx()).isEqualTo(3L);
    }

    @Test
    void 나가기_마지막_1명_남으면_방_유지() {
        // 카카오톡식(I-2): 한 명 나가도 1명 남으면 휴면방으로 유지(소프트딜리트 안 함)
        RoomMember rm = new RoomMember();
        ReflectionTestUtils.setField(rm, "user", userA);
        ReflectionTestUtils.setField(rm, "role", "MEMBER");

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(Optional.of(rm));
        given(roomMemberRepository.save(any(RoomMember.class))).willAnswer(inv -> inv.getArgument(0));
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));
        // 나간 뒤에도 1명(userB) 남음
        given(roomMemberRepository.findAllActiveByRoom(room)).willReturn(List.of(roomMember(userB)));

        chatService.leaveRoom("a@test.com", 1L);

        assertThat(room.getDelDate()).isNull();
    }

    @Test
    void 나가기_마지막_0명이면_방_소프트딜리트() {
        // 카카오톡식(I-2): 마지막 멤버가 나가 활성 0명이면 채팅방 소프트딜리트
        RoomMember rm = new RoomMember();
        ReflectionTestUtils.setField(rm, "user", userA);
        ReflectionTestUtils.setField(rm, "role", "MEMBER");

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(userA));
        given(roomMemberRepository.findByChatRoomAndUserAndExitAtIsNull(room, userA)).willReturn(Optional.of(rm));
        given(roomMemberRepository.save(any(RoomMember.class))).willAnswer(inv -> inv.getArgument(0));
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));
        given(roomMemberRepository.findAllActiveByRoom(room)).willReturn(List.of());
        given(chatRoomRepository.save(any(ChatRoom.class))).willAnswer(inv -> inv.getArgument(0));

        chatService.leaveRoom("a@test.com", 1L);

        assertThat(room.getDelDate()).isNotNull();
    }
}
