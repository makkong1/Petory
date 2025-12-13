package com.linkup.Petory.domain.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleOAuth2UserService extends DefaultOAuth2UserService {

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("========== Google OAuth2 사용자 정보 로드 시작 ==========");

        // 기본 OAuth2User 로드
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // 모든 attributes 로그 출력 (상세)
        attributes.forEach((key, value) -> {
            // 값이 너무 길면 잘라서 표시
            String valueStr = value != null ? value.toString() : "null";
            if (valueStr.length() > 200) {
                valueStr = valueStr.substring(0, 200) + "... (길이: " + valueStr.length() + ")";
            }
            log.info("  [{}] = {}", key, valueStr);
        });

        // 전역 컬렉터에 저장 (DB에 저장하지 않는 값들도 확인 가능)
        OAuth2DataCollector.saveProviderData("google", attributes);

        // Google의 경우 추가 처리가 필요 없으면 그대로 반환
        // 필요시 여기서 attributes를 변환하거나 추가 정보를 설정할 수 있음
        return new DefaultOAuth2User(
                oAuth2User.getAuthorities(),
                attributes,
                "sub" // Google의 사용자 식별자 필드명
        );
    }
}
