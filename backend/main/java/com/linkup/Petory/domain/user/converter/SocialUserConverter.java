package com.linkup.Petory.domain.user.converter;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.user.dto.SocialUserDTO;
import com.linkup.Petory.domain.user.entity.Provider;
import com.linkup.Petory.domain.user.entity.SocialUser;
import com.linkup.Petory.domain.user.entity.Users;

@Component
public class SocialUserConverter {

    public SocialUserDTO toDTO(SocialUser socialUser) {
        return new SocialUserDTO(
                socialUser.getIdx(),
                socialUser.getProvider().name(),
                socialUser.getProviderId()
        );
    }

    public SocialUser toEntity(SocialUserDTO dto, Users user) {
        return SocialUser.builder()
                .idx(dto.idx())
                .user(user)
                .provider(Provider.valueOf(dto.provider()))
                .providerId(dto.providerId())
                .build();
    }
}
