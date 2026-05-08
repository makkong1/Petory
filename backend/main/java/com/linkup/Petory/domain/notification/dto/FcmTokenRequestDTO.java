package com.linkup.Petory.domain.notification.dto;

import com.linkup.Petory.domain.notification.entity.FcmToken;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FcmTokenRequestDTO {
    private String token;
    private FcmToken.DeviceType deviceType;
}
