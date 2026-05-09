package com.linkup.Petory.domain.notification.dto;

import com.linkup.Petory.domain.notification.entity.FcmToken;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FcmTokenRequestDTO {
    @NotBlank
    private String token;

    @NotNull
    private FcmToken.DeviceType deviceType;
}
