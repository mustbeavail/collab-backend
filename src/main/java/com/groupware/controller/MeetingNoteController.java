package com.groupware.controller;

import com.groupware.dto.common.ApiResponse;
import com.groupware.dto.minutes.AiGenerateRequest;
import com.groupware.dto.minutes.CreateMeetingNoteRequest;
import com.groupware.dto.minutes.MeetingNoteResponse;
import com.groupware.dto.minutes.UpdateMeetingNoteRequest;
import com.groupware.service.MeetingNoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/minutes")
@RequiredArgsConstructor
public class MeetingNoteController {

    private final MeetingNoteService meetingNoteService;

    @GetMapping("/rooms/{roomIdx}")
    public ResponseEntity<ApiResponse<List<MeetingNoteResponse>>> getNotes(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomIdx) {
        return ResponseEntity.ok(ApiResponse.ok(
                meetingNoteService.getNotesByRoom(userDetails.getUsername(), roomIdx)));
    }

    @PostMapping("/rooms/{roomIdx}/voice-generate")
    public ResponseEntity<ApiResponse<MeetingNoteResponse>> generateVoiceMinutes(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomIdx,
            @RequestParam("audio") List<MultipartFile> audioFiles) {
        return ResponseEntity.ok(ApiResponse.ok(
                meetingNoteService.generateVoiceMinutes(userDetails.getUsername(), roomIdx, audioFiles)));
    }

    @PostMapping("/rooms/{roomIdx}/ai-generate")
    public ResponseEntity<ApiResponse<MeetingNoteResponse>> generateAiMinutes(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomIdx,
            @Valid @RequestBody AiGenerateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                meetingNoteService.generateAiMinutes(userDetails.getUsername(), roomIdx, request)));
    }

    @PostMapping("/rooms/{roomIdx}")
    public ResponseEntity<ApiResponse<MeetingNoteResponse>> createNote(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomIdx,
            @Valid @RequestBody CreateMeetingNoteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                meetingNoteService.createNote(userDetails.getUsername(), roomIdx, request)));
    }

    @PutMapping("/{noteIdx}")
    public ResponseEntity<ApiResponse<MeetingNoteResponse>> updateNote(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long noteIdx,
            @Valid @RequestBody UpdateMeetingNoteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                meetingNoteService.updateNote(userDetails.getUsername(), noteIdx, request)));
    }

    @DeleteMapping("/{noteIdx}")
    public ResponseEntity<ApiResponse<Void>> deleteNote(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long noteIdx) {
        meetingNoteService.deleteNote(userDetails.getUsername(), noteIdx);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
