package com.groupware.controller;

import com.groupware.dto.common.ApiResponse;
import com.groupware.dto.search.GlobalSearchResponse;
import com.groupware.dto.search.RoomSearchResponse;
import com.groupware.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/global")
    public ResponseEntity<ApiResponse<GlobalSearchResponse>> globalSearch(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String q) {
        if (q == null || q.isBlank() || q.length() < 1) {
            return ResponseEntity.ok(ApiResponse.ok(
                    GlobalSearchResponse.builder().friends(java.util.List.of()).teams(java.util.List.of()).rooms(java.util.List.of()).build()));
        }
        return ResponseEntity.ok(ApiResponse.ok(
                searchService.globalSearch(userDetails.getUsername(), q)));
    }

    @GetMapping("/rooms/{roomIdx}")
    public ResponseEntity<ApiResponse<RoomSearchResponse>> roomSearch(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomIdx,
            @RequestParam String q) {
        if (q == null || q.isBlank() || q.length() < 1) {
            return ResponseEntity.ok(ApiResponse.ok(
                    RoomSearchResponse.builder().messages(java.util.List.of()).files(java.util.List.of()).schedules(java.util.List.of()).notes(java.util.List.of()).build()));
        }
        return ResponseEntity.ok(ApiResponse.ok(
                searchService.roomSearch(userDetails.getUsername(), roomIdx, q)));
    }
}
