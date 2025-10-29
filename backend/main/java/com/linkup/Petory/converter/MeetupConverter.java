package com.linkup.Petory.converter;

import com.linkup.Petory.dto.MeetupDTO;
import com.linkup.Petory.dto.MeetupParticipantsDTO;
import com.linkup.Petory.entity.Meetup;
import com.linkup.Petory.entity.MeetupParticipants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class MeetupConverter {

    @Autowired
    private MeetupParticipantsConverter meetupParticipantsConverter;

    public MeetupDTO toDTO(Meetup meetup) {
        if (meetup == null)
            return null;

        List<MeetupParticipantsDTO> participantsDTO = null;
        if (meetup.getParticipants() != null) {
            participantsDTO = meetup.getParticipants().stream()
                    .map(meetupParticipantsConverter::toDTO)
                    .collect(Collectors.toList());
        }

        return MeetupDTO.builder()
                .idx(meetup.getIdx())
                .title(meetup.getTitle())
                .description(meetup.getDescription())
                .location(meetup.getLocation())
                .latitude(meetup.getLatitude())
                .longitude(meetup.getLongitude())
                .date(meetup.getDate())
                .organizerIdx(meetup.getOrganizer().getIdx())
                .organizerName(meetup.getOrganizer().getUsername())
                .maxParticipants(meetup.getMaxParticipants())
                .currentParticipants(meetup.getParticipants() != null ? meetup.getParticipants().size() : 0)
                .createdAt(meetup.getCreatedAt())
                .updatedAt(meetup.getUpdatedAt())
                .participants(participantsDTO)
                .build();
    }

    public Meetup toEntity(MeetupDTO dto) {
        if (dto == null)
            return null;

        return Meetup.builder()
                .idx(dto.getIdx())
                .title(dto.getTitle())
                .description(dto.getDescription())
                .location(dto.getLocation())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .date(dto.getDate())
                .maxParticipants(dto.getMaxParticipants())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }
}
