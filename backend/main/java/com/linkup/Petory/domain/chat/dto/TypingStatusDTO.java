package com.linkup.Petory.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TypingStatusDTO {
    private final Long userId;
    private final boolean typing;
}
