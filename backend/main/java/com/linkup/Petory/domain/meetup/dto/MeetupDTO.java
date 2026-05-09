package com.linkup.Petory.domain.meetup.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetupDTO {

    private Long idx;
    @NotBlank private String title;
    private String description;
    @NotBlank private String location;
    private Double latitude;
    private Double longitude;
    @NotNull private LocalDateTime date;
    private Long organizerIdx;
    private String organizerName;
    @NotNull @Min(2) private Integer maxParticipants;
    private Integer currentParticipants; // 현재 참여자 수
    private String status; // 모임 상태: RECRUITING, CLOSED, COMPLETED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @JsonIgnore
    private Boolean isDeleted;
    @JsonIgnore
    private LocalDateTime deletedAt;
    private List<MeetupParticipantsDTO> participants;
}
