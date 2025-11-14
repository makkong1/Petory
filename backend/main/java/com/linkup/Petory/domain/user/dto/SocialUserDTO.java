package com.linkup.Petory.domain.user.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialUserDTO {
    private Long idx;
    private String provider;
    private String providerId;
}
