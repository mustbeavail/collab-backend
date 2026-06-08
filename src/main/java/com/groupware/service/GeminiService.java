package com.groupware.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GeminiService {

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final long MAX_INLINE_AUDIO_BYTES = 15L * 1024 * 1024;
    private static final String AUDIO_MINUTES_PROMPT =
            "위 음성/영상 파일은 팀 회의 녹음입니다. 내용을 듣고 한국어로 회의록을 작성해 주세요.\n\n" +
            "회의록은 반드시 아래 형식을 따르세요:\n\n" +
            "## 참석자\n(발언자 목록)\n\n" +
            "## 대화 전문\n(주요 대화 내용을 원문에 가깝게 정리)\n\n" +
            "## 주요 논의 내용\n(핵심 안건과 논의 사항을 항목별로 정리)\n\n" +
            "## 결정사항\n(회의에서 결정된 내용)\n\n" +
            "## 액션 아이템\n(후속 할 일, 담당자, 기한 등)";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public GeminiService(
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.model:gemini-2.5-flash}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public String generateContent(String prompt) {
        String url = String.format(GEMINI_URL, model, apiKey);

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.7,
                        "maxOutputTokens", 8192
                )
        );

        return callGemini(url, body);
    }

    public String generateContentFromAudio(byte[] audioData, String mimeType) {
        if (audioData.length > MAX_INLINE_AUDIO_BYTES) {
            throw new CustomException(ErrorCode.AUDIO_TOO_LARGE);
        }

        String url = String.format(GEMINI_URL, model, apiKey);
        String base64Audio = Base64.getEncoder().encodeToString(audioData);

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(
                                Map.of("inlineData", Map.of("mimeType", mimeType, "data", base64Audio)),
                                Map.of("text", AUDIO_MINUTES_PROMPT)
                        )
                )),
                "generationConfig", Map.of("temperature", 0.7, "maxOutputTokens", 8192)
        );

        return callGemini(url, body);
    }

    public String mergeMinutesSections(List<String> sections) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sections.size(); i++) {
            sb.append("=== 구간 ").append(i + 1).append(" ===\n").append(sections.get(i)).append("\n\n");
        }
        return generateContent(
                "다음은 하나의 회의를 여러 구간으로 나누어 작성한 부분 회의록들입니다. " +
                "이를 하나의 완성된 회의록으로 통합해 주세요.\n\n" +
                "통합 회의록은 다음 형식을 따르세요:\n\n" +
                "## 참석자\n(발언자 목록)\n\n" +
                "## 대화 전문\n(주요 대화 내용을 시간순으로 정리)\n\n" +
                "## 주요 논의 내용\n(핵심 안건과 논의 사항을 항목별로 정리)\n\n" +
                "## 결정사항\n(회의에서 결정된 내용)\n\n" +
                "## 액션 아이템\n(후속 할 일, 담당자, 기한 등)\n\n" +
                "---부분 회의록---\n" + sb
        );
    }

    private String callGemini(String url, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
        } catch (RestClientException e) {
            log.error("Gemini API 호출 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.AI_GENERATION_FAILED);
        } catch (Exception e) {
            log.error("Gemini 응답 파싱 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.AI_GENERATION_FAILED);
        }
    }
}
