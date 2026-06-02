package com.linkup.Petory.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 타이핑 상태 응답 DTO. 어떤 사용자가 현재 입력 중인지 여부를 담는다. */
@Getter
@AllArgsConstructor
public class TypingStatusDTO {
    private final Long userId;
    private final boolean typing;
}
