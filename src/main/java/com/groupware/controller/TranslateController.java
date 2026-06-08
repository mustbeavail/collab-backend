package com.groupware.controller;

import com.groupware.dto.common.ApiResponse;
import com.groupware.dto.translate.TranslateRequest;
import com.groupware.dto.translate.TranslateResponse;
import com.groupware.service.TranslateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/translate")
@RequiredArgsConstructor
public class TranslateController {

    private final TranslateService translateService;

    @PostMapping
    public ResponseEntity<ApiResponse<TranslateResponse>> translate(
            @Valid @RequestBody TranslateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(translateService.translate(request)));
    }
}
