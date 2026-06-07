package com.groupware.controller;

import com.groupware.dto.common.ApiResponse;
import com.groupware.dto.file.FileResponseDto;
import com.groupware.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileResponseDto>> upload(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Long roomIdx,
            @RequestPart MultipartFile file) {
        FileResponseDto dto = fileService.upload(userDetails.getUsername(), roomIdx, file);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FileResponseDto>>> getFiles(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Long roomIdx) {
        List<FileResponseDto> files = fileService.getFiles(userDetails.getUsername(), roomIdx);
        return ResponseEntity.ok(ApiResponse.ok(files));
    }

    @GetMapping("/{fileIdx}/download")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long fileIdx) {
        Resource resource = fileService.download(userDetails.getUsername(), fileIdx);
        String encoded = URLEncoder.encode(resource.getFilename() != null ? resource.getFilename() : "file",
                StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/{fileIdx}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long fileIdx) {
        fileService.delete(userDetails.getUsername(), fileIdx);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
