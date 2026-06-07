package com.groupware.service;

import com.groupware.domain.*;
import com.groupware.dto.search.GlobalSearchResponse;
import com.groupware.dto.search.RoomSearchResponse;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @InjectMocks private SearchService searchService;
    @Mock private UserRepository userRepository;
    @Mock private FriendRepository friendRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private UploadFileRepository uploadFileRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private MeetingNoteRepository meetingNoteRepository;

    private User user;
    private User friendUser;

    @BeforeEach
    void setUp() {
        user = new User();
        ReflectionTestUtils.setField(user, "userId", "a@test.com");
        ReflectionTestUtils.setField(user, "nick", "Alice");

        friendUser = new User();
        ReflectionTestUtils.setField(friendUser, "userId", "bob@test.com");
        ReflectionTestUtils.setField(friendUser, "nick", "Bob");
    }

    private Friend makeFriend(User requester, User receiver) {
        Friend f = new Friend();
        ReflectionTestUtils.setField(f, "user", requester);
        ReflectionTestUtils.setField(f, "friend", receiver);
        ReflectionTestUtils.setField(f, "status", "ACCEPTED");
        return f;
    }

    private Team makeTeam(Long idx, String name) {
        Team t = new Team();
        ReflectionTestUtils.setField(t, "teamIdx", idx);
        ReflectionTestUtils.setField(t, "teamName", name);
        return t;
    }

    private ChatRoom makeRoom(Long idx, String name, Team team) {
        ChatRoom r = new ChatRoom();
        ReflectionTestUtils.setField(r, "roomIdx", idx);
        ReflectionTestUtils.setField(r, "roomName", name);
        ReflectionTestUtils.setField(r, "team", team);
        return r;
    }

    private Message makeMessage(Long idx, String content, User sender) {
        Message m = new Message();
        ReflectionTestUtils.setField(m, "msgIdx", idx);
        ReflectionTestUtils.setField(m, "content", content);
        ReflectionTestUtils.setField(m, "user", sender);
        ReflectionTestUtils.setField(m, "sentAt", LocalDateTime.of(2026, 6, 1, 12, 0));
        return m;
    }

    @Test
    void 글로벌검색_친구_매칭() {
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(friendRepository.findAcceptedFriends(user)).willReturn(List.of(makeFriend(user, friendUser)));
        given(teamRepository.findActiveTeamsByUserId("a@test.com")).willReturn(List.of());
        given(chatRoomRepository.searchRoomsByUser(eq("a@test.com"), anyString())).willReturn(List.of());

        GlobalSearchResponse result = searchService.globalSearch("a@test.com", "bob");

        assertThat(result.getFriends()).hasSize(1);
        assertThat(result.getFriends().get(0).getNickname()).isEqualTo("Bob");
        assertThat(result.getTeams()).isEmpty();
        assertThat(result.getRooms()).isEmpty();
    }

    @Test
    void 글로벌검색_팀_매칭() {
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(friendRepository.findAcceptedFriends(user)).willReturn(List.of());
        given(teamRepository.findActiveTeamsByUserId("a@test.com"))
                .willReturn(List.of(makeTeam(1L, "개발팀"), makeTeam(2L, "마케팅팀")));
        given(chatRoomRepository.searchRoomsByUser(eq("a@test.com"), anyString())).willReturn(List.of());

        GlobalSearchResponse result = searchService.globalSearch("a@test.com", "개발");

        assertThat(result.getTeams()).hasSize(1);
        assertThat(result.getTeams().get(0).getTeamName()).isEqualTo("개발팀");
    }

    @Test
    void 글로벌검색_채팅방_매칭() {
        given(userRepository.findById("a@test.com")).willReturn(Optional.of(user));
        given(friendRepository.findAcceptedFriends(user)).willReturn(List.of());
        given(teamRepository.findActiveTeamsByUserId("a@test.com")).willReturn(List.of());
        given(chatRoomRepository.searchRoomsByUser("a@test.com", "general"))
                .willReturn(List.of(makeRoom(1L, "general", makeTeam(1L, "개발팀"))));

        GlobalSearchResponse result = searchService.globalSearch("a@test.com", "general");

        assertThat(result.getRooms()).hasSize(1);
        assertThat(result.getRooms().get(0).getRoomName()).isEqualTo("general");
        assertThat(result.getRooms().get(0).isDm()).isFalse();
    }

    @Test
    void 글로벌검색_사용자없음_예외() {
        given(userRepository.findById("x@test.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> searchService.globalSearch("x@test.com", "test"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 채팅방검색_메시지_매칭() {
        given(messageRepository.searchByContent(eq(1L), eq("안녕"), any(Pageable.class)))
                .willReturn(List.of(makeMessage(10L, "안녕하세요", user)));
        given(uploadFileRepository.searchByFilename(1L, "안녕")).willReturn(List.of());
        given(scheduleRepository.searchByTitle(1L, "안녕")).willReturn(List.of());
        given(meetingNoteRepository.searchByRoomAndKeyword(1L, "안녕")).willReturn(List.of());

        RoomSearchResponse result = searchService.roomSearch("a@test.com", 1L, "안녕");

        assertThat(result.getMessages()).hasSize(1);
        assertThat(result.getMessages().get(0).getContent()).isEqualTo("안녕하세요");
        assertThat(result.getMessages().get(0).getSenderNick()).isEqualTo("Alice");
        assertThat(result.getFiles()).isEmpty();
        assertThat(result.getSchedules()).isEmpty();
        assertThat(result.getNotes()).isEmpty();
    }

    @Test
    void 채팅방검색_빈쿼리_결과없음() {
        given(messageRepository.searchByContent(eq(1L), eq("없는내용"), any(Pageable.class)))
                .willReturn(List.of());
        given(uploadFileRepository.searchByFilename(1L, "없는내용")).willReturn(List.of());
        given(scheduleRepository.searchByTitle(1L, "없는내용")).willReturn(List.of());
        given(meetingNoteRepository.searchByRoomAndKeyword(1L, "없는내용")).willReturn(List.of());

        RoomSearchResponse result = searchService.roomSearch("a@test.com", 1L, "없는내용");

        assertThat(result.getMessages()).isEmpty();
        assertThat(result.getFiles()).isEmpty();
        assertThat(result.getSchedules()).isEmpty();
        assertThat(result.getNotes()).isEmpty();
    }
}
