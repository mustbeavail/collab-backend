package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.MeetingNote;
import com.groupware.domain.Message;
import com.groupware.domain.User;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

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

    @Transactional(readOnly = true)
    public List<MeetingNoteResponse> getNotesByRoom(String userId, Long roomIdx) {
        ChatRoom room = getActiveRoom(roomIdx);
        User user = getActiveUser(userId);
        checkAccess(room, user);
        return meetingNoteRepository
                .findByChatRoomRoomIdxAndDelAtIsNullOrderByCreatedAtDesc(roomIdx)
                .stream().map(MeetingNoteResponse::from).collect(Collectors.toList());
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

        List<Message> messages = messageRepository.findTextMessagesByRoomAndTimeRange(
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
               .append(m.getUser().getNick()).append(": ")
               .append(m.getContent()).append("\n");
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

    @Transactional
    public void deleteNote(String userId, Long noteIdx) {
        MeetingNote note = meetingNoteRepository.findByNoteIdxAndDelAtIsNull(noteIdx)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_NOTE_NOT_FOUND));
        User user = getActiveUser(userId);
        checkAccess(note.getChatRoom(), user);
        note.setDelAt(LocalDateTime.now());
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
