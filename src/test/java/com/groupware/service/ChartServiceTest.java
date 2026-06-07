package com.groupware.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupware.domain.ChatRoom;
import com.groupware.domain.User;
import com.groupware.dto.chart.ChartAnalyzeRequest;
import com.groupware.dto.chart.ChartAnalyzeResponse;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ChartServiceTest {

    @InjectMocks private ChartService chartService;
    @Mock private GeminiService geminiService;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User user;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(chartService, "objectMapper", objectMapper);

        user = new User();
        user.setUserId("alice@test.com");
        user.setNick("Alice");

        room = new ChatRoom();
        room.setRoomIdx(1L);
    }

    private ChartAnalyzeRequest buildRequest(Long roomIdx) {
        ChartAnalyzeRequest req = new ChartAnalyzeRequest();
        req.setRoomIdx(roomIdx);
        req.setTableData(List.of(
                List.of("월", "판매량"),
                List.of("1월", 100),
                List.of("2월", 200)
        ));
        return req;
    }

    @Test
    void analyze_returns_chart_response_on_success() throws Exception {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("alice@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);

        String geminiJson = """
                {
                  "chartType": "bar",
                  "labels": ["1월", "2월"],
                  "datasets": [{"label": "판매량", "data": [100, 200]}]
                }
                """;
        given(geminiService.generateContent(anyString())).willReturn(geminiJson);

        ChartAnalyzeResponse result = chartService.analyze("alice@test.com", buildRequest(1L));

        assertThat(result.getChartType()).isEqualTo("bar");
        assertThat(result.getLabels()).containsExactly("1월", "2월");
        assertThat(result.getDatasets()).hasSize(1);
        assertThat(result.getDatasets().get(0).getLabel()).isEqualTo("판매량");
    }

    @Test
    void analyze_strips_markdown_codeblock_from_gemini_response() throws Exception {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("alice@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);

        String geminiWrapped = "```json\n{\"chartType\":\"pie\",\"labels\":[\"A\"],\"datasets\":[]}\n```";
        given(geminiService.generateContent(anyString())).willReturn(geminiWrapped);

        ChartAnalyzeResponse result = chartService.analyze("alice@test.com", buildRequest(1L));

        assertThat(result.getChartType()).isEqualTo("pie");
    }

    @Test
    void analyze_throws_CHAT_ROOM_NOT_FOUND_when_room_missing() {
        given(chatRoomRepository.findById(99L)).willReturn(Optional.empty());

        ChartAnalyzeRequest req = buildRequest(99L);
        assertThatThrownBy(() -> chartService.analyze("alice@test.com", req))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void analyze_throws_NOT_ROOM_MEMBER_when_not_member() {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("alice@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(false);

        assertThatThrownBy(() -> chartService.analyze("alice@test.com", buildRequest(1L)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_ROOM_MEMBER);
    }

    @Test
    void analyze_throws_AI_GENERATION_FAILED_on_invalid_json() {
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(userRepository.findById("alice@test.com")).willReturn(Optional.of(user));
        given(roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)).willReturn(true);
        given(geminiService.generateContent(anyString())).willReturn("not valid json");

        assertThatThrownBy(() -> chartService.analyze("alice@test.com", buildRequest(1L)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_GENERATION_FAILED);
    }
}
