package com.linkup.Petory.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetupParticipantsDTO {

    private Long meetupIdx;
    private Long userIdx;
    private String username; // 참여자명
    private LocalDateTime joinedAt;
}
