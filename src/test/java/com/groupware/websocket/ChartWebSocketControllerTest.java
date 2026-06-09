package com.groupware.websocket;

import com.groupware.domain.User;
import com.groupware.dto.chart.ChartAnalyzeResponse;
import com.groupware.dto.chart.ChartSharePayload;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.UserRepository;
import com.groupware.service.RoomMembershipChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChartWebSocketControllerTest {

    @InjectMocks private ChartWebSocketController controller;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private UserRepository userRepository;
    @Mock private RoomMembershipChecker roomMembershipChecker;

    private Principal principal(String name) {
        return () -> name;
    }

    @Test
    void shareChart_broadcasts_payload_to_topic() {
        User user = new User();
        user.setUserId("alice@test.com");
        user.setNick("Alice");
        given(userRepository.findById("alice@test.com")).willReturn(Optional.of(user));

        ChartSharePayload payload = new ChartSharePayload();
        payload.setChartConfig(new ChartAnalyzeResponse());

        controller.shareChart(1L, payload, principal("alice@test.com"));

        ArgumentCaptor<ChartSharePayload> captor = ArgumentCaptor.forClass(ChartSharePayload.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/chart/1"), captor.capture());
        assertThat(captor.getValue().getFromUserId()).isEqualTo("alice@test.com");
        assertThat(captor.getValue().getFromNickname()).isEqualTo("Alice");
    }

    @Test
    void shareChart_ignores_null_principal() {
        controller.shareChart(1L, new ChartSharePayload(), null);
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void shareChart_uses_userId_as_nickname_when_user_not_found() {
        given(userRepository.findById("ghost@test.com")).willReturn(Optional.empty());

        ChartSharePayload payload = new ChartSharePayload();
        controller.shareChart(1L, payload, principal("ghost@test.com"));

        ArgumentCaptor<ChartSharePayload> captor = ArgumentCaptor.forClass(ChartSharePayload.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/chart/1"), captor.capture());
        assertThat(captor.getValue().getFromNickname()).isEqualTo("ghost@test.com");
    }

    @Test
    void shareChart_throws_and_does_not_broadcast_when_not_member() {
        doThrow(new CustomException(ErrorCode.NOT_ROOM_MEMBER))
                .when(roomMembershipChecker).check(1L, "intruder@test.com");

        assertThatThrownBy(() ->
                controller.shareChart(1L, new ChartSharePayload(), principal("intruder@test.com")))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_ROOM_MEMBER);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}
