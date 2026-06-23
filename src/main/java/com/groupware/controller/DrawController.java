package com.groupware.controller;

import com.groupware.dto.common.ApiResponse;
import com.groupware.dto.draw.DrawEventPayload.DrawElement;
import com.groupware.service.DrawStateStore;
import com.groupware.service.RoomMembershipChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 항목8(일정이후): 그림판 캔버스 스냅샷 조회.
 * 패널을 (다시) 열 때 그 이전에 그려진 내용을 받아 초기 표시한다. 방 멤버만 조회 가능.
 */
@RestController
@RequestMapping("/api/draw")
@RequiredArgsConstructor
public class DrawController {

    private final DrawStateStore drawStateStore;
    private final RoomMembershipChecker roomMembershipChecker;

    @GetMapping("/rooms/{roomIdx}")
    public ResponseEntity<ApiResponse<List<DrawElement>>> getState(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomIdx) {
        roomMembershipChecker.check(roomIdx, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(drawStateStore.snapshot(roomIdx)));
    }
}
