package com.linkup.Petory.domain.meetup.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
/**
 * 모임 응답/요청 DTO. 모임 정보·참여자 목록·좋아요 수·현재 사용자 참여 여부를 포함한다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetupDTO {

    private Long idx;
    @NotBlank
    private String title;
    private String description;
    @NotBlank
    private String location;
    private Double latitude;
    private Double longitude;
    private Double distance; // 미터 단위, 위치 기반 조회 응답에서만 설정
    @NotNull
    private LocalDateTime date;
    private Long organizerIdx;
    private String organizerName;
    @NotNull
    @Min(2)
    private Integer maxParticipants;
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
