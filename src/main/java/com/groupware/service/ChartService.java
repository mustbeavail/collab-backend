package com.groupware.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupware.domain.ChatRoom;
import com.groupware.domain.User;
import com.groupware.dto.chart.ChartAnalyzeRequest;
import com.groupware.dto.chart.ChartAnalyzeResponse;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChartService {

    private final GeminiService geminiService;
    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ChartAnalyzeResponse analyze(String userId, ChartAnalyzeRequest request) {
        ChatRoom room = chatRoomRepository.findById(request.getRoomIdx())
                .filter(r -> r.getDelDate() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)) {
            throw new CustomException(ErrorCode.NOT_ROOM_MEMBER);
        }

        String csvData = buildCsv(request.getTableData());
        String prompt = buildPrompt(csvData, request.getQuestion());

        String raw = geminiService.generateContent(prompt);
        String json = stripMarkdown(raw);

        try {
            return objectMapper.readValue(json, ChartAnalyzeResponse.class);
        } catch (Exception e) {
            log.error("차트 JSON 파싱 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.AI_GENERATION_FAILED);
        }
    }

    private String buildCsv(List<List<Object>> tableData) {
        return tableData.stream()
                .map(row -> row.stream()
                        .map(cell -> cell == null ? "" : cell.toString())
                        .collect(Collectors.joining(",")))
                .collect(Collectors.joining("\n"));
    }

    private String buildPrompt(String csvData, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음 CSV 데이터를 분석하여 Chart.js에서 사용할 수 있는 차트 설정을 JSON 형식으로 반환하세요.\n");
        sb.append("반드시 JSON만 반환하고, 마크다운 코드블록 없이 순수 JSON만 출력하세요.\n\n");
        sb.append("CSV 데이터:\n").append(csvData).append("\n\n");
        if (question != null && !question.isBlank()) {
            sb.append("분석 요청: ").append(question).append("\n\n");
        }
        sb.append("응답 형식 (예시):\n");
        sb.append("{\n");
        sb.append("  \"chartType\": \"bar\",\n");
        sb.append("  \"labels\": [\"항목1\", \"항목2\"],\n");
        sb.append("  \"datasets\": [\n");
        sb.append("    {\n");
        sb.append("      \"label\": \"데이터셋 이름\",\n");
        sb.append("      \"data\": [10, 20],\n");
        sb.append("      \"backgroundColor\": [\"rgba(99,102,241,0.6)\", \"rgba(34,197,94,0.6)\"],\n");
        sb.append("      \"borderColor\": [\"rgba(99,102,241,1)\", \"rgba(34,197,94,1)\"],\n");
        sb.append("      \"borderWidth\": 1\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");
        sb.append("chartType은 bar, line, pie, doughnut, radar 중 데이터에 가장 적합한 것을 선택하세요.");
        return sb.toString();
    }

    private String stripMarkdown(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastBacktick = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastBacktick > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastBacktick).trim();
            }
        }
        return trimmed;
    }
}
