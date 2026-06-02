package com.linkup.Petory.domain.notification.dto;

import com.linkup.Petory.domain.notification.entity.FcmToken;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** FCM 토큰 등록/삭제 요청 DTO. 토큰 문자열과 디바이스 유형(ANDROID/IOS)을 담는다. */
@Getter
@NoArgsConstructor
public class FcmTokenRequestDTO {
    @NotBlank
    private String token;

    @NotNull
    private FcmToken.DeviceType deviceType;
}
