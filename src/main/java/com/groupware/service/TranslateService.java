package com.groupware.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupware.dto.translate.TranslateRequest;
import com.groupware.dto.translate.TranslateResponse;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
public class TranslateService {

    private static final String TRANSLATE_URL =
            "https://translation.googleapis.com/language/translate/v2?key=%s";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;

    public TranslateService(@Value("${google.translate.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    public TranslateResponse translate(TranslateRequest request) {
        String url = String.format(TRANSLATE_URL, apiKey);

        Map<String, Object> body = Map.of(
                "q", request.getText(),
                "target", request.getTargetLang(),
                "format", "text"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, httpRequest, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String translated = root.path("data").path("translations").get(0).path("translatedText").asText();
            return new TranslateResponse(translated);
        } catch (RestClientException e) {
            log.error("Google Translate API 호출 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.TRANSLATE_FAILED);
        } catch (Exception e) {
            log.error("Google Translate 응답 파싱 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.TRANSLATE_FAILED);
        }
    }
}
