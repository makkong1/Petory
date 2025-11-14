package com.linkup.Petory.domain.meetup.converter;

import com.linkup.Petory.domain.meetup.dto.MeetupParticipantsDTO;
import com.linkup.Petory.domain.meetup.entity.MeetupParticipants;

import org.springframework.stereotype.Component;

@Component
public class MeetupParticipantsConverter {

    public MeetupParticipantsDTO toDTO(MeetupParticipants participants) {
        if (participants == null)
            return null;

        return MeetupParticipantsDTO.builder()
                .meetupIdx(participants.getMeetup().getIdx())
                .userIdx(participants.getUser().getIdx())
                .username(participants.getUser().getUsername())
                .joinedAt(participants.getJoinedAt())
                .build();
    }

    public MeetupParticipants toEntity(MeetupParticipantsDTO dto) {
        if (dto == null)
            return null;

        return MeetupParticipants.builder()
                .joinedAt(dto.getJoinedAt())
                .build();
    }
}
