package com.linkup.Petory.domain.meetup.dto;

import java.time.LocalDateTime;

/**
 * 모임 참여자 정보 DTO (record)
 * - 불변 객체로 응답 데이터의 의도치 않은 변경 방지
 */
public record MeetupParticipantsDTO(
    Long meetupIdx,
    Long userIdx,
    String username,        // 참여자명
    LocalDateTime joinedAt
) {}
