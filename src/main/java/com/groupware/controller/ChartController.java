package com.groupware.controller;

import com.groupware.dto.chart.ChartAnalyzeRequest;
import com.groupware.dto.chart.ChartAnalyzeResponse;
import com.groupware.dto.chart.ChartSharePayload;
import com.groupware.dto.common.ApiResponse;
import com.groupware.service.ChartService;
import com.groupware.service.ChartStateStore;
import com.groupware.service.RoomMembershipChecker;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chart")
@RequiredArgsConstructor
public class ChartController {

    private final ChartService chartService;
    private final ChartStateStore chartStateStore;
    private final RoomMembershipChecker roomMembershipChecker;

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<ChartAnalyzeResponse>> analyze(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChartAnalyzeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                chartService.analyze(userDetails.getUsername(), request)));
    }

    /**
     * 항목4(일정이후): 방의 현재 공유 차트 스냅샷. 패널을 (다시) 열 때 그 전에 공유된 차트를 받아 표시.
     * 공유된 차트가 없으면 data=null. 방 멤버만 조회 가능.
     */
    @GetMapping("/rooms/{roomIdx}")
    public ResponseEntity<ApiResponse<ChartSharePayload>> getSharedChart(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomIdx) {
        roomMembershipChecker.check(roomIdx, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(chartStateStore.get(roomIdx)));
    }
}
