package com.linkup.Petory.domain.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Component;

/**
 * Provider별로 다른 TokenResponseClient를 사용하는 래퍼 클라이언트
 * Naver는 커스텀 클라이언트 사용, Google 등은 기본 클라이언트 사용
 */
@Slf4j
@Component
public class ConditionalOAuth2TokenResponseClient
        implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

    private final NaverOAuth2TokenResponseClient naverClient;
    private OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> defaultClient;

    public ConditionalOAuth2TokenResponseClient(NaverOAuth2TokenResponseClient naverClient) {
        this.naverClient = naverClient;
    }

    private OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> getDefaultClient() {
        if (defaultClient == null) {
            // Spring Security 6.4+ 권장 클라이언트
            // RestClient 기반 클라이언트 사용 (DefaultAuthorizationCodeTokenResponseClient의 대체)
            defaultClient = new org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient();
        }
        return defaultClient;
    }

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(
            OAuth2AuthorizationCodeGrantRequest authorizationCodeGrantRequest) {

        String registrationId = authorizationCodeGrantRequest.getClientRegistration().getRegistrationId();

        log.info("OAuth2 토큰 응답 요청: provider={}", registrationId);

        // Naver인 경우 커스텀 클라이언트 사용
        if ("naver".equalsIgnoreCase(registrationId)) {
            log.info("Naver 커스텀 클라이언트 사용");
            return naverClient.getTokenResponse(authorizationCodeGrantRequest);
        }

        // Google 등 다른 provider는 기본 클라이언트 사용
        log.info("{} provider는 기본 클라이언트 사용", registrationId);
        return getDefaultClient().getTokenResponse(authorizationCodeGrantRequest);
    }
}
