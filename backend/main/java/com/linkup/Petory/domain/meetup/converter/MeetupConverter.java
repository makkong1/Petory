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
import java.util.Objects;
import java.util.stream.Collectors;

/** Meetup 엔티티 → MeetupDTO 변환기. */
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

        Integer maxParticipants = meetup.getMaxParticipants();
        Integer currentParticipants = meetup.getCurrentParticipants();

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
                .maxParticipants(Objects.requireNonNullElse(maxParticipants, 10))
                .currentParticipants(Objects.requireNonNullElse(currentParticipants, 0))
                .status(meetup.getStatus() != null ? meetup.getStatus().name() : MeetupStatus.RECRUITING.name())
                .createdAt(meetup.getCreatedAt())
                .updatedAt(meetup.getUpdatedAt())
                .isDeleted(meetup.getIsDeleted())
                .deletedAt(meetup.getDeletedAt())
                .participants(participantsDTO)
                .build();
    }

    public List<MeetupDTO> toDTOList(List<Meetup> meetups) {
        if (meetups == null) {
            return List.of();
        }
        return meetups.stream().map(this::toDTO).collect(Collectors.toList());
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

        Integer dtoMaxParticipants = dto.getMaxParticipants();
        Integer dtoCurrentParticipants = dto.getCurrentParticipants();

        return Meetup.builder()
                .idx(dto.getIdx())
                .title(dto.getTitle())
                .description(dto.getDescription())
                .location(dto.getLocation())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .date(dto.getDate())
                .maxParticipants(Objects.requireNonNullElse(dtoMaxParticipants, 10))
                .currentParticipants(Objects.requireNonNullElse(dtoCurrentParticipants, 0))
                .status(status)
                .isDeleted(dto.getIsDeleted())
                .deletedAt(dto.getDeletedAt())
                // createdAt, updatedAt은 BaseTimeEntity에서 자동 관리되므로 제거
                .build();
    }
}
