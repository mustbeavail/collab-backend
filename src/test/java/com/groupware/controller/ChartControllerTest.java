package com.groupware.controller;

import com.groupware.dto.chart.ChartSharePayload;
import com.groupware.dto.common.ApiResponse;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.service.ChartService;
import com.groupware.service.ChartStateStore;
import com.groupware.service.RoomMembershipChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class ChartControllerTest {

    @InjectMocks private ChartController controller;
    @Mock private ChartService chartService;
    @Mock private ChartStateStore chartStateStore;
    @Mock private RoomMembershipChecker roomMembershipChecker;

    private UserDetails user(String name) {
        return new User(name, "", Collections.emptyList());
    }

    @Test
    void getSharedChart_방의_최신차트_반환() {
        ChartSharePayload payload = new ChartSharePayload();
        payload.setFromUserId("alice@test.com");
        given(chartStateStore.get(1L)).willReturn(payload);

        ResponseEntity<ApiResponse<ChartSharePayload>> res =
                controller.getSharedChart(user("alice@test.com"), 1L);

        assertThat(res.getBody().getData()).isSameAs(payload);
    }

    @Test
    void getSharedChart_공유차트_없으면_null() {
        given(chartStateStore.get(1L)).willReturn(null);

        ResponseEntity<ApiResponse<ChartSharePayload>> res =
                controller.getSharedChart(user("alice@test.com"), 1L);

        assertThat(res.getBody().getData()).isNull();
    }

    @Test
    void getSharedChart_비멤버면_예외() {
        doThrow(new CustomException(ErrorCode.NOT_ROOM_MEMBER))
                .when(roomMembershipChecker).check(1L, "intruder@test.com");

        assertThatThrownBy(() -> controller.getSharedChart(user("intruder@test.com"), 1L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_ROOM_MEMBER);
    }
}
