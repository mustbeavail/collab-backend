package com.groupware.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupware.domain.ChatRoom;
import com.groupware.domain.MeetingNote;
import com.groupware.domain.Message;
import com.groupware.domain.TeamMember;
import com.groupware.domain.User;
import com.groupware.dto.minutes.MinutesTimeRangeResponse;
import com.groupware.dto.minutes.AiGenerateRequest;
import com.groupware.dto.minutes.CreateMeetingNoteRequest;
import com.groupware.dto.minutes.MeetingNoteResponse;
import com.groupware.dto.minutes.UpdateMeetingNoteRequest;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.MeetingNoteRepository;
import com.groupware.repository.MessageRepository;
import com.groupware.repository.RoomMemberRepository;
import com.groupware.repository.TeamMemberRepository;
import com.groupware.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingNoteService {

    private final MeetingNoteRepository meetingNoteRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final GeminiService geminiService;

    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter MSG_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Transactional(readOnly = true)
    public List<MeetingNoteResponse> getNotesByRoom(String userId, Long roomIdx) {
        ChatRoom room = getActiveRoom(roomIdx);
        User user = getActiveUser(userId);
        checkAccess(room, user);
        return meetingNoteRepository
                .findByChatRoomRoomIdxAndDelAtIsNullOrderByCreatedAtDesc(roomIdx)
                .stream().map(MeetingNoteResponse::from).collect(Collectors.toList());
    }

    // 항목2(일정이후): AI 회의록 시간범위 디폴트 — 방의 가장 오래된/최신 메시지 시각
    @Transactional(readOnly = true)
    public MinutesTimeRangeResponse getMessageTimeRange(String userId, Long roomIdx) {
        ChatRoom room = getActiveRoom(roomIdx);
        User user = getActiveUser(userId);
        checkAccess(room, user);
        return MinutesTimeRangeResponse.of(
                messageRepository.findEarliestSentAt(roomIdx),
                messageRepository.findLatestSentAt(roomIdx));
    }

    @Transactional
    public MeetingNoteResponse createNote(String userId, Long roomIdx, CreateMeetingNoteRequest req) {
        ChatRoom room = getActiveRoom(roomIdx);
        User user = getActiveUser(userId);
        checkAccess(room, user);

        MeetingNote note = new MeetingNote();
        note.setChatRoom(room);
        note.setUser(user);
        note.setTitle(req.getTitle());
        note.setContent(req.getContent());
        return MeetingNoteResponse.from(meetingNoteRepository.save(note));
    }

    @Transactional
    public MeetingNoteResponse updateNote(String userId, Long noteIdx, UpdateMeetingNoteRequest req) {
        MeetingNote note = meetingNoteRepository.findByNoteIdxAndDelAtIsNull(noteIdx)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_NOTE_NOT_FOUND));
        User user = getActiveUser(userId);
        if (!note.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.MEETING_NOTE_ACCESS_DENIED);
        }
        note.setTitle(req.getTitle());
        note.setContent(req.getContent());
        return MeetingNoteResponse.from(note);
    }

    @Transactional
    public MeetingNoteResponse generateAiMinutes(String userId, Long roomIdx, AiGenerateRequest req) {
        ChatRoom room = getActiveRoom(roomIdx);
        User user = getActiveUser(userId);
        checkAccess(room, user);

        // 항목4(일정이후): TEXT + FILE 메시지 모두 포함(파일은 '이름 : 파일명'으로 정리)
        List<Message> messages = messageRepository.findMessagesForMinutes(
                roomIdx, req.getStartTime(), req.getEndTime());

        if (messages.isEmpty()) {
            throw new CustomException(ErrorCode.NO_MESSAGES_IN_RANGE);
        }

        String prompt = buildPrompt(messages, req.getStartTime(), req.getEndTime());
        String aiContent = geminiService.generateContent(prompt);

        String title = "AI 회의록 - " + req.getStartTime().format(DISPLAY_FMT)
                + " ~ " + req.getEndTime().format(DISPLAY_FMT);

        MeetingNote note = new MeetingNote();
        note.setChatRoom(room);
        note.setUser(user);
        note.setTitle(title);
        note.setContent(aiContent);
        return MeetingNoteResponse.from(meetingNoteRepository.save(note));
    }

    private String buildPrompt(List<Message> messages, LocalDateTime start, LocalDateTime end) {
        StringBuilder log = new StringBuilder();
        for (Message m : messages) {
            log.append("[").append(m.getSentAt().format(MSG_FMT)).append("] ")
               .append(m.getUser().getNick());
            if ("FILE".equals(m.getMsgType())) {
                // 항목4: 파일 전송 메시지는 '이름 : (파일 전송) 파일명' 형식으로 포함
                log.append(" : (파일 전송) ").append(extractFileName(m.getContent()));
            } else {
                log.append(": ").append(m.getContent());
            }
            log.append("\n");
        }

        return """
                다음은 채팅방의 대화 기록입니다. 이를 바탕으로 회의록을 한국어로 작성해주세요.

                회의록은 반드시 아래 형식을 따르세요:

                ## 참석자
                (대화에 등장한 사람들)

                ## 대화 전문
                (주요 대화 내용을 원문에 가깝게 정리)

                ## 주요 논의 내용
                (핵심 안건과 논의 사항을 항목별로 정리)

                ## 결정사항
                (회의에서 결정된 내용)

                ## 액션 아이템
                (후속 할 일, 담당자, 기한 등)

                ---대화 기록 (""" + messages.size() + "개 메시지)---\n" + log.toString();
    }

    // FILE 메시지 content(JSON: {fileIdx, oriFilename, ...})에서 파일명 추출. 실패 시 '파일'.
    private String extractFileName(String content) {
        if (content == null || content.isBlank()) return "파일";
        try {
            JsonNode node = OBJECT_MAPPER.readTree(content);
            String name = node.path("oriFilename").asText(null);
            return (name != null && !name.isBlank()) ? name : "파일";
        } catch (Exception e) {
            return "파일";
        }
    }

    @Transactional
    public MeetingNoteResponse generateVoiceMinutes(String userId, Long roomIdx, List<MultipartFile> audioFiles) {
        ChatRoom room = getActiveRoom(roomIdx);
        User user = getActiveUser(userId);
        checkAccess(room, user);

        if (audioFiles == null || audioFiles.isEmpty()) {
            throw new CustomException(ErrorCode.NO_AUDIO_FILES);
        }

        List<String> partialMinutes = new ArrayList<>();
        try {
            for (MultipartFile file : audioFiles) {
                String mimeType = file.getContentType() != null ? file.getContentType() : "audio/webm";
                partialMinutes.add(geminiService.generateContentFromAudio(file.getBytes(), mimeType));
            }
        } catch (IOException e) {
            log.error("오디오 파일 읽기 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.AI_GENERATION_FAILED);
        }

        String aiContent = partialMinutes.size() == 1
                ? partialMinutes.get(0)
                : geminiService.mergeMinutesSections(partialMinutes);

        String title = "음성 AI 회의록 - " + LocalDateTime.now().format(DISPLAY_FMT);

        MeetingNote note = new MeetingNote();
        note.setChatRoom(room);
        note.setUser(user);
        note.setTitle(title);
        note.setContent(aiContent);
        return MeetingNoteResponse.from(meetingNoteRepository.save(note));
    }

    @Transactional
    public void deleteNote(String userId, Long noteIdx) {
        MeetingNote note = meetingNoteRepository.findByNoteIdxAndDelAtIsNull(noteIdx)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_NOTE_NOT_FOUND));
        User user = getActiveUser(userId);
        ChatRoom room = note.getChatRoom();
        checkAccess(room, user);
        // 항목6(일정이후): 삭제는 작성자 본인 OR 방장(비팀방)/팀 리더·매니저(팀방)만
        if (!canDelete(note, room, user)) {
            throw new CustomException(ErrorCode.MEETING_NOTE_DELETE_DENIED);
        }
        note.setDelAt(LocalDateTime.now());
    }

    private boolean canDelete(MeetingNote note, ChatRoom room, User user) {
        // 작성자 본인은 항상 삭제 가능
        if (note.getUser().getUserId().equals(user.getUserId())) {
            return true;
        }
        if (room.getTeam() != null) {
            // 팀 채팅방: 팀 리더·매니저만
            String role = teamMemberRepository
                    .findActiveByTeamIdxAndUserId(room.getTeam().getTeamIdx(), user.getUserId())
                    .map(TeamMember::getRole)
                    .orElse(null);
            return "LEADER".equals(role) || "MANAGER".equals(role);
        } else {
            // 비팀 채팅방: 방장(OWNER)만
            String role = roomMemberRepository
                    .findByChatRoomAndUserAndExitAtIsNull(room, user)
                    .map(com.groupware.domain.RoomMember::getRole)
                    .orElse(null);
            return "OWNER".equals(role);
        }
    }

    private ChatRoom getActiveRoom(Long roomIdx) {
        return chatRoomRepository.findById(roomIdx)
                .filter(r -> r.getDelDate() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    private User getActiveUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private void checkAccess(ChatRoom room, User user) {
        if (room.getTeam() != null) {
            teamMemberRepository.findActiveByTeamIdxAndUserId(
                    room.getTeam().getTeamIdx(), user.getUserId())
                    .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAM_MEMBER));
        } else {
            if (!roomMemberRepository.existsByChatRoomAndUserAndExitAtIsNull(room, user)) {
                throw new CustomException(ErrorCode.NOT_ROOM_MEMBER);
            }
        }
    }
}
