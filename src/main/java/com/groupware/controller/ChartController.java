package com.groupware.controller;

import com.groupware.dto.chart.ChartAnalyzeRequest;
import com.groupware.dto.chart.ChartAnalyzeResponse;
import com.groupware.dto.common.ApiResponse;
import com.groupware.service.ChartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chart")
@RequiredArgsConstructor
public class ChartController {

    private final ChartService chartService;

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<ChartAnalyzeResponse>> analyze(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChartAnalyzeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                chartService.analyze(userDetails.getUsername(), request)));
    }
}
