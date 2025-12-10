package com.linkup.Petory.domain.user.service;

import com.linkup.Petory.domain.user.service.OAuth2DataCollector;
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
public class NaverOAuth2UserService extends DefaultOAuth2UserService {

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("========== Naver OAuth2 사용자 정보 로드 시작 ==========");

        // 기본 OAuth2User 로드
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // 원본 attributes 전체 로그 출력 (상세)
        log.info("========================================");
        log.info("📋 Naver OAuth2 원본 Attributes (총 {}개):", attributes.size());
        log.info("========================================");
        attributes.forEach((key, value) -> {
            // 값이 너무 길면 잘라서 표시
            String valueStr = value != null ? value.toString() : "null";
            if (valueStr.length() > 200) {
                valueStr = valueStr.substring(0, 200) + "... (길이: " + valueStr.length() + ")";
            }
            log.info("  [{}] = {}", key, valueStr);
        });
        log.info("========================================");
        
        // 전역 컬렉터에 저장 (원본 attributes)
        OAuth2DataCollector.saveProviderData("naver_original", attributes);

        // Naver는 response 객체 안에 사용자 정보가 있음
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");
        
        if (response == null) {
            log.error("❌ Naver response 객체가 null입니다. 원본 attributes: {}", attributes);
            throw new OAuth2AuthenticationException("Naver 사용자 정보를 가져올 수 없습니다.");
        }

        // response 객체 전체 로그 출력 (상세)
        log.info("========================================");
        log.info("📋 Naver Response 객체 내용 (총 {}개):", response.size());
        log.info("========================================");
        response.forEach((key, value) -> {
            // 값이 너무 길면 잘라서 표시
            String valueStr = value != null ? value.toString() : "null";
            if (valueStr.length() > 200) {
                valueStr = valueStr.substring(0, 200) + "... (길이: " + valueStr.length() + ")";
            }
            log.info("  [{}] = {}", key, valueStr);
        });
        log.info("========================================");
        
        // 전역 컬렉터에 저장 (response 객체)
        OAuth2DataCollector.saveProviderData("naver_response", response);

        // 주요 필드 상세 로그
        log.info("📌 Naver 주요 정보:");
        log.info("  - id (Provider ID): {}", response.get("id"));
        log.info("  - email: {}", response.get("email"));
        log.info("  - name: {}", response.get("name"));
        log.info("  - nickname: {}", response.get("nickname"));
        log.info("  - profile_image: {}", response.get("profile_image"));
        log.info("  - age: {}", response.get("age"));
        log.info("  - gender: {}", response.get("gender"));
        log.info("  - mobile: {}", response.get("mobile"));
        log.info("  - mobile_e164: {}", response.get("mobile_e164"));
        log.info("  - birthday: {}", response.get("birthday"));
        log.info("  - birthyear: {}", response.get("birthyear"));

        log.info("========== Naver OAuth2 사용자 정보 로드 완료 ==========");

        // Naver의 response 객체를 attributes로 변환하여 표준화
        // 이렇게 하면 OAuth2Service에서 일관된 방식으로 처리 가능
        return new DefaultOAuth2User(
                oAuth2User.getAuthorities(),
                response, // response 객체를 직접 attributes로 사용
                "id" // Naver의 사용자 식별자 필드명
        );
    }
}

