package com.linkup.Petory.converter;

import com.linkup.Petory.dto.MeetupParticipantsDTO;
import com.linkup.Petory.entity.MeetupParticipants;
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
