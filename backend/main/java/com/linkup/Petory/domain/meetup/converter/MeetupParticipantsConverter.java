package com.linkup.Petory.domain.meetup.converter;

import com.linkup.Petory.domain.meetup.dto.MeetupParticipantsDTO;
import com.linkup.Petory.domain.meetup.entity.MeetupParticipants;

import org.springframework.stereotype.Component;

@Component
public class MeetupParticipantsConverter {

    public MeetupParticipantsDTO toDTO(MeetupParticipants participants) {
        if (participants == null)
            return null;

        return new MeetupParticipantsDTO(
                participants.getMeetup().getIdx(),
                participants.getUser().getIdx(),
                participants.getUser().getUsername(),
                participants.getJoinedAt()
        );
    }

    public MeetupParticipants toEntity(MeetupParticipantsDTO dto) {
        if (dto == null)
            return null;

        return MeetupParticipants.builder()
                .joinedAt(dto.joinedAt())
                .build();
    }
}
