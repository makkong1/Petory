package com.linkup.Petory.domain.meetup.converter;

import com.linkup.Petory.domain.meetup.dto.MeetupDTO;
import com.linkup.Petory.domain.meetup.dto.MeetupParticipantsDTO;
import com.linkup.Petory.domain.meetup.entity.Meetup;
import com.linkup.Petory.domain.meetup.entity.MeetupParticipants;
import com.linkup.Petory.domain.meetup.entity.MeetupStatus;
import com.linkup.Petory.domain.user.entity.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MeetupConverter {

    private final MeetupParticipantsConverter meetupParticipantsConverter;

    public MeetupDTO toDTO(Meetup meetup) {
        if (meetup == null) {
            return null;
        }

        List<MeetupParticipantsDTO> participantsDTO = null;
        List<MeetupParticipants> participants = meetup.getParticipants();
        if (participants != null) {
            participantsDTO = participants.stream()
                    .map(meetupParticipantsConverter::toDTO)
                    .collect(Collectors.toList());
        }

        Users organizer = meetup.getOrganizer();

        return MeetupDTO.builder()
                .idx(meetup.getIdx())
                .title(meetup.getTitle())
                .description(meetup.getDescription())
                .location(meetup.getLocation())
                .latitude(meetup.getLatitude())
                .longitude(meetup.getLongitude())
                .date(meetup.getDate())
                .organizerIdx(organizer != null ? organizer.getIdx() : null)
                .organizerName(organizer != null ? organizer.getUsername() : null)
                .maxParticipants(meetup.getMaxParticipants())
                .currentParticipants(meetup.getCurrentParticipants() != null ? meetup.getCurrentParticipants() : 0)
                .status(meetup.getStatus() != null ? meetup.getStatus().name() : MeetupStatus.RECRUITING.name())
                .createdAt(meetup.getCreatedAt())
                .updatedAt(meetup.getUpdatedAt())
                .isDeleted(meetup.getIsDeleted())
                .deletedAt(meetup.getDeletedAt())
                .participants(participantsDTO)
                .build();
    }

    public Meetup toEntity(MeetupDTO dto) {
        if (dto == null) {
            return null;
        }

        MeetupStatus status = MeetupStatus.RECRUITING;
        if (dto.getStatus() != null) {
            try {
                status = MeetupStatus.valueOf(dto.getStatus());
            } catch (IllegalArgumentException e) {
                status = MeetupStatus.RECRUITING;
            }
        }

        return Meetup.builder()
                .idx(dto.getIdx())
                .title(dto.getTitle())
                .description(dto.getDescription())
                .location(dto.getLocation())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .date(dto.getDate())
                .maxParticipants(dto.getMaxParticipants())
                .currentParticipants(dto.getCurrentParticipants() != null ? dto.getCurrentParticipants() : 0)
                .status(status)
                .isDeleted(dto.getIsDeleted())
                .deletedAt(dto.getDeletedAt())
                // createdAt, updatedAt은 BaseTimeEntity에서 자동 관리되므로 제거
                .build();
    }
}
