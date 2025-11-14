package com.linkup.Petory.domain.user.converter;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.user.dto.SocialUserDTO;
import com.linkup.Petory.domain.user.entity.Provider;
import com.linkup.Petory.domain.user.entity.SocialUser;
import com.linkup.Petory.domain.user.entity.Users;

@Component
public class SocialUserConverter {

    public SocialUserDTO toDTO(SocialUser socialUser) {
        return SocialUserDTO.builder()
                .idx(socialUser.getIdx())
                .provider(socialUser.getProvider().name())
                .providerId(socialUser.getProviderId())
                .build();
    }

    public SocialUser toEntity(SocialUserDTO dto, Users user) {
        return SocialUser.builder()
                .idx(dto.getIdx())
                .user(user)
                .provider(Provider.valueOf(dto.getProvider()))
                .providerId(dto.getProviderId())
                .build();
    }
}
