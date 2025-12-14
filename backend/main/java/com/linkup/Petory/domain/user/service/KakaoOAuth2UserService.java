package com.linkup.Petory.domain.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoOAuth2UserService extends DefaultOAuth2UserService {

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("========== Kakao OAuth2 사용자 정보 로드 시작 ==========");

        // 기본 OAuth2User 로드
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // 전역 컬렉터에 저장 (원본 attributes)
        OAuth2DataCollector.saveProviderData("kakao_original", attributes);

        // Kakao 데이터 추출 및 평탄화 (Flattening)
        // Kakao의 attributes 구조:
        // {
        // "id": 123456789,
        // "kakao_account": {
        // "profile": { "nickname": "...", "profile_image_url": "..." },
        // "email": "...",
        // "age_range": "...",
        // "birthday": "...",
        // "gender": "..."
        // }
        // }

        Map<String, Object> newAttributes = new HashMap<>();
        newAttributes.put("id", String.valueOf(attributes.get("id")));
        newAttributes.put("connected_at", attributes.get("connected_at"));

        @SuppressWarnings("unchecked")
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        if (kakaoAccount != null) {
            newAttributes.put("email", kakaoAccount.get("email"));
            newAttributes.put("age_range", kakaoAccount.get("age_range"));
            newAttributes.put("birthday", kakaoAccount.get("birthday")); // MMDD
            newAttributes.put("birthyear", kakaoAccount.get("birthyear")); // YYYY (권한 필요)
            newAttributes.put("gender", kakaoAccount.get("gender"));
            newAttributes.put("phone_number", kakaoAccount.get("phone_number"));

            @SuppressWarnings("unchecked")
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
            if (profile != null) {
                newAttributes.put("nickname", profile.get("nickname"));
                newAttributes.put("profile_image", profile.get("profile_image_url"));
                newAttributes.put("thumbnail_image", profile.get("thumbnail_image_url"));
            }
        }

        // 전역 컬렉터에 저장 (평탄화된 데이터)
        OAuth2DataCollector.saveProviderData("kakao_flattened", newAttributes);

        log.info("========== Kakao OAuth2 사용자 정보 로드 완료 (평탄화됨) ==========");

        return new DefaultOAuth2User(
                oAuth2User.getAuthorities(),
                newAttributes,
                "id" // Kakao의 사용자 식별자 필드명
        );
    }
}
