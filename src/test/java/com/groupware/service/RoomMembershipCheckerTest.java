package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.Team;
import com.groupware.domain.TeamMember;
import com.groupware.domain.User;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.TeamMemberRepository;
import com.groupware.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RoomMembershipCheckerTest {

    @InjectMocks private RoomMembershipChecker checker;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private UserRepository userRepository;

    private User user;
    private ChatRoom dmRoom;     // team == null → 방 멤버십으로 판단
    private ChatRoom teamRoom;   // team != null → 팀 멤버십으로 판단

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUserId("alice@test.com");

        dmRoom = new ChatRoom();
        dmRoom.setRoomIdx(1L);

        Team team = new Team();
        team.setTeamIdx(7L);
        teamRoom = new ChatRoom();
        teamRoom.setRoomIdx(2L);
        teamRoom.setTeam(team);
    }

    // ── check (예외 던지는 버전) ───────────────────────

    @Test
    void check_passes_for_room_member() {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(dmRoom));
        given(userRepository.findById("alice@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(dmRoom, user)).willReturn(true);

        assertThatCode(() -> checker.check(1L, "alice@test.com")).doesNotThrowAnyException();
    }

    @Test
    void check_passes_for_team_member() {
        given(chatRoomRepository.findById(2L)).willReturn(Optional.of(teamRoom));
        given(userRepository.findById("alice@test.com")).willReturn(Optional.of(user));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(7L, "alice@test.com"))
                .willReturn(Optional.of(new TeamMember()));

        assertThatCode(() -> checker.check(2L, "alice@test.com")).doesNotThrowAnyException();
    }

    @Test
    void check_throws_CHAT_ROOM_NOT_FOUND_when_room_missing() {
        given(chatRoomRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> checker.check(99L, "alice@test.com"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void check_throws_CHAT_ROOM_NOT_FOUND_when_room_soft_deleted() {
        dmRoom.setDelDate(LocalDateTime.now());
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(dmRoom));

        assertThatThrownBy(() -> checker.check(1L, "alice@test.com"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void check_throws_USER_NOT_FOUND_when_user_missing() {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(dmRoom));
        given(userRepository.findById("ghost@test.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> checker.check(1L, "ghost@test.com"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void check_throws_NOT_ROOM_MEMBER_for_non_member_dm() {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(dmRoom));
        given(userRepository.findById("alice@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(dmRoom, user)).willReturn(false);

        assertThatThrownBy(() -> checker.check(1L, "alice@test.com"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_ROOM_MEMBER);
    }

    @Test
    void check_throws_NOT_TEAM_MEMBER_for_non_member_team_room() {
        given(chatRoomRepository.findById(2L)).willReturn(Optional.of(teamRoom));
        given(userRepository.findById("alice@test.com")).willReturn(Optional.of(user));
        given(teamMemberRepository.findActiveByTeamIdxAndUserId(7L, "alice@test.com"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> checker.check(2L, "alice@test.com"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_TEAM_MEMBER);
    }

    // ── isMember (boolean 버전) ────────────────────────

    @Test
    void isMember_true_for_member() {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(dmRoom));
        given(userRepository.findById("alice@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(dmRoom, user)).willReturn(true);

        assertThat(checker.isMember(1L, "alice@test.com")).isTrue();
    }

    @Test
    void isMember_false_for_non_member() {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(dmRoom));
        given(userRepository.findById("alice@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(dmRoom, user)).willReturn(false);

        assertThat(checker.isMember(1L, "alice@test.com")).isFalse();
    }

    @Test
    void isMember_false_when_room_missing() {
        given(chatRoomRepository.findById(99L)).willReturn(Optional.empty());

        assertThat(checker.isMember(99L, "alice@test.com")).isFalse();
    }

    @Test
    void isMember_false_when_user_missing() {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(dmRoom));
        given(userRepository.findById("ghost@test.com")).willReturn(Optional.empty());

        assertThat(checker.isMember(1L, "ghost@test.com")).isFalse();
    }
}
