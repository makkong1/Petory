package com.linkup.Petory.domain.meetup.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 사용자의 모임 기록 응답 DTO. 주최자는 모임 생성 시 자동 참가자로 저장되므로 participants 기준 한 번의 조회로 주최/참가
 * 이력을 함께 표현한다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetupHistoryDTO {

    private Long meetupIdx;
    private String title;
    private String location;
    private LocalDateTime date;
    private String status;
    private Long organizerIdx;
    private String organizerName;
    private LocalDateTime joinedAt;
    private String participationRole;
    private Boolean liked;
}
