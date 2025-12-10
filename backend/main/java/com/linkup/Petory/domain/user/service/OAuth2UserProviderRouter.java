package com.linkup.Petory.domain.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserProviderRouter extends DefaultOAuth2UserService {

    private final GoogleOAuth2UserService googleOAuth2UserService;
    private final NaverOAuth2UserService naverOAuth2UserService;
    // 추후 KakaoOAuth2UserService 등 추가 가능
    // private final KakaoOAuth2UserService kakaoOAuth2UserService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String provider = userRequest.getClientRegistration().getRegistrationId(); // "google", "naver" 등
        log.info("OAuth2 사용자 로드 요청: provider={}", provider);

        return switch (provider.toLowerCase()) {
            case "google" -> googleOAuth2UserService.loadUser(userRequest);
            case "naver" -> naverOAuth2UserService.loadUser(userRequest);
            // case "kakao" -> kakaoOAuth2UserService.loadUser(userRequest);
            default -> throw new OAuth2AuthenticationException("지원하지 않는 소셜 로그인입니다: " + provider);
        };
    }
}

