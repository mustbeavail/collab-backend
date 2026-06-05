package com.groupware.service;

import com.groupware.domain.ChatRoom;
import com.groupware.domain.Team;
import com.groupware.domain.TeamMember;
import com.groupware.dto.team.TeamChannelDto;
import com.groupware.dto.team.TeamMemberDto;
import com.groupware.dto.team.TeamSidebarResponse;
import com.groupware.repository.ChatRoomRepository;
import com.groupware.repository.TeamMemberRepository;
import com.groupware.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ChatRoomRepository chatRoomRepository;

    @Transactional(readOnly = true)
    public List<TeamSidebarResponse> getMyTeams(String userId) {
        List<Team> teams = teamRepository.findActiveTeamsByUserId(userId);

        return teams.stream().map(team -> {
            List<TeamMember> members = teamMemberRepository.findActiveByTeamIdx(team.getTeamIdx());
            List<ChatRoom> channels = chatRoomRepository.findByTeamTeamIdxAndDelDateIsNull(team.getTeamIdx());

            String myRole = members.stream()
                    .filter(m -> m.getUser().getUserId().equals(userId))
                    .map(TeamMember::getRole)
                    .findFirst()
                    .orElse("MEMBER");

            return TeamSidebarResponse.builder()
                    .teamIdx(team.getTeamIdx())
                    .teamName(team.getTeamName())
                    .myRole(myRole)
                    .channels(channels.stream()
                            .map(ch -> new TeamChannelDto(ch.getRoomIdx(), ch.getRoomName()))
                            .toList())
                    .members(members.stream()
                            .map(m -> new TeamMemberDto(m.getUser().getUserId(), m.getUser().getNick(), m.getRole()))
                            .toList())
                    .build();
        }).toList();
    }
}
