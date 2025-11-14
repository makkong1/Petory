package com.linkup.Petory.domain.meetup.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetupDTO {

    private Long idx;
    private String title;
    private String description;
    private String location;
    private Double latitude;
    private Double longitude;
    private LocalDateTime date;
    private Long organizerIdx;
    private String organizerName; // 주최자명
    private Integer maxParticipants;
    private Integer currentParticipants; // 현재 참여자 수
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<MeetupParticipantsDTO> participants;
}
