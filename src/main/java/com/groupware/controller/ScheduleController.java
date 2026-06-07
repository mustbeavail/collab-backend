package com.groupware.controller;

import com.groupware.dto.common.ApiResponse;
import com.groupware.dto.schedule.CreateScheduleRequest;
import com.groupware.dto.schedule.ScheduleResponse;
import com.groupware.dto.schedule.UpdateScheduleRequest;
import com.groupware.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> getMySchedules(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                scheduleService.getMySchedules(userDetails.getUsername())));
    }

    @GetMapping("/rooms/{roomIdx}")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> getSchedules(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomIdx) {
        return ResponseEntity.ok(ApiResponse.ok(
                scheduleService.getSchedulesByRoom(userDetails.getUsername(), roomIdx)));
    }

    @PostMapping("/rooms/{roomIdx}")
    public ResponseEntity<ApiResponse<ScheduleResponse>> createSchedule(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomIdx,
            @Valid @RequestBody CreateScheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                scheduleService.createSchedule(userDetails.getUsername(), roomIdx, request)));
    }

    @PutMapping("/{scheduleIdx}")
    public ResponseEntity<ApiResponse<ScheduleResponse>> updateSchedule(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long scheduleIdx,
            @Valid @RequestBody UpdateScheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                scheduleService.updateSchedule(userDetails.getUsername(), scheduleIdx, request)));
    }

    @DeleteMapping("/{scheduleIdx}")
    public ResponseEntity<ApiResponse<Void>> deleteSchedule(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long scheduleIdx) {
        scheduleService.deleteSchedule(userDetails.getUsername(), scheduleIdx);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
