package com.groupware.service;

import com.groupware.domain.Friend;
import com.groupware.domain.Team;
import com.groupware.domain.User;
import com.groupware.dto.search.GlobalSearchResponse;
import com.groupware.dto.search.RoomSearchResponse;
import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import com.groupware.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class SearchService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final int MAX_RESULTS = 20;

    private final UserRepository userRepository;
    private final FriendRepository friendRepository;
    private final TeamRepository teamRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final MessageRepository messageRepository;
    private final UploadFileRepository uploadFileRepository;
    private final ScheduleRepository scheduleRepository;
    private final MeetingNoteRepository meetingNoteRepository;

    @Transactional(readOnly = true)
    public GlobalSearchResponse globalSearch(String userId, String q) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        String lower = q.toLowerCase();

        List<GlobalSearchResponse.FriendResult> friends = friendRepository
                .findAcceptedFriends(user)
                .stream()
                .map(f -> f.getUser().getUserId().equals(userId) ? f.getFriend() : f.getUser())
                .filter(u -> u.getNick().toLowerCase().contains(lower) || u.getUserId().toLowerCase().contains(lower))
                .limit(MAX_RESULTS)
                .map(u -> GlobalSearchResponse.FriendResult.builder()
                        .userId(u.getUserId())
                        .nickname(u.getNick())
                        .avatarUrl(u.getAvatarUrl())
                        .build())
                .collect(Collectors.toList());

        List<GlobalSearchResponse.TeamResult> teams = teamRepository
                .findActiveTeamsByUserId(userId)
                .stream()
                .filter(t -> t.getTeamName().toLowerCase().contains(lower))
                .limit(MAX_RESULTS)
                .map(t -> GlobalSearchResponse.TeamResult.builder()
                        .teamIdx(t.getTeamIdx())
                        .teamName(t.getTeamName())
                        .build())
                .collect(Collectors.toList());

        List<GlobalSearchResponse.RoomResult> rooms = chatRoomRepository
                .searchRoomsByUser(userId, q)
                .stream()
                .limit(MAX_RESULTS)
                .map(r -> GlobalSearchResponse.RoomResult.builder()
                        .roomIdx(r.getRoomIdx())
                        .roomName(r.getRoomName())
                        .isDm(r.getTeam() == null)
                        .build())
                .collect(Collectors.toList());

        return GlobalSearchResponse.builder()
                .friends(friends)
                .teams(teams)
                .rooms(rooms)
                .build();
    }

    @Transactional(readOnly = true)
    public RoomSearchResponse roomSearch(String userId, Long roomIdx, String q) {
        List<RoomSearchResponse.MessageResult> messages = messageRepository
                .searchByContent(roomIdx, q, PageRequest.of(0, MAX_RESULTS))
                .stream()
                .map(m -> RoomSearchResponse.MessageResult.builder()
                        .msgIdx(m.getMsgIdx())
                        .content(m.getContent())
                        .senderNick(m.getUser().getNick())
                        .sentAt(m.getSentAt() != null ? m.getSentAt().format(FMT) : null)
                        .build())
                .collect(Collectors.toList());

        List<RoomSearchResponse.FileResult> files = uploadFileRepository
                .searchByFilename(roomIdx, q)
                .stream()
                .limit(MAX_RESULTS)
                .map(f -> RoomSearchResponse.FileResult.builder()
                        .fileIdx(f.getFileIdx())
                        .filename(f.getOriFilename())
                        .fileSize(f.getFileSize())
                        .uploaderNick(f.getUser().getNick())
                        .uploadedAt(f.getCreatedAt() != null ? f.getCreatedAt().format(FMT) : null)
                        .build())
                .collect(Collectors.toList());

        List<RoomSearchResponse.ScheduleResult> schedules = scheduleRepository
                .searchByTitle(roomIdx, q)
                .stream()
                .limit(MAX_RESULTS)
                .map(s -> RoomSearchResponse.ScheduleResult.builder()
                        .scheduleIdx(s.getScheduleIdx())
                        .title(s.getTitle())
                        .date(s.getAppointmentDate() != null ? s.getAppointmentDate().format(FMT) : null)
                        .build())
                .collect(Collectors.toList());

        List<RoomSearchResponse.NoteResult> notes = meetingNoteRepository
                .searchByRoomAndKeyword(roomIdx, q)
                .stream()
                .limit(MAX_RESULTS)
                .map(n -> RoomSearchResponse.NoteResult.builder()
                        .noteIdx(n.getNoteIdx())
                        .title(n.getTitle())
                        .creatorNick(n.getUser().getNick())
                        .createdAt(n.getCreatedAt() != null ? n.getCreatedAt().format(FMT) : null)
                        .build())
                .collect(Collectors.toList());

        return RoomSearchResponse.builder()
                .messages(messages)
                .files(files)
                .schedules(schedules)
                .notes(notes)
                .build();
    }
}
