package com.linkup.Petory.domain.meetup.converter;

import com.linkup.Petory.domain.meetup.dto.MeetupParticipantsDTO;
import com.linkup.Petory.domain.meetup.entity.MeetupParticipants;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class MeetupParticipantsConverter {

    public MeetupParticipantsDTO toDTO(MeetupParticipants participants) {
        if (participants == null)
            return null;

        return new MeetupParticipantsDTO(
                participants.getMeetup().getIdx(),
                participants.getUser().getIdx(),
                participants.getUser().getUsername(),
                participants.getJoinedAt(),
                participants.getLiked() != null ? participants.getLiked() : false
        );
    }

    public List<MeetupParticipantsDTO> toDTOList(List<MeetupParticipants> participants) {
        if (participants == null) {
            return List.of();
        }
        return participants.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public MeetupParticipants toEntity(MeetupParticipantsDTO dto) {
        if (dto == null)
            return null;

        return MeetupParticipants.builder()
                .joinedAt(dto.joinedAt())
                .liked(dto.liked() != null ? dto.liked() : false)
                .build();
    }
}
