package com.linkup.Petory.domain.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NaverOAuth2TokenResponseClient
        implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

    private final RestOperations restOperations;

    public NaverOAuth2TokenResponseClient() {
        this.restOperations = new RestTemplate();
    }

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(
            OAuth2AuthorizationCodeGrantRequest authorizationCodeGrantRequest) {
        String registrationId = authorizationCodeGrantRequest.getClientRegistration().getRegistrationId();

        // Naver가 아닌 경우 기본 Spring Security 처리를 사용하려고 하면
        // 순환 참조 문제가 발생할 수 있으므로, Naver만 처리하고
        // 다른 provider는 SecurityConfig에서 별도로 설정해야 함
        // 하지만 현재는 모든 provider에 이 클라이언트가 적용되므로
        // Naver가 아닌 경우 예외를 발생시켜서 문제를 명확히 함
        if (!"naver".equalsIgnoreCase(registrationId)) {
            log.error("❌ NaverOAuth2TokenResponseClient는 Naver 전용입니다. provider: {}", registrationId);
            log.error("Google은 SecurityConfig에서 별도의 클라이언트를 설정해야 합니다.");
            throw new IllegalArgumentException(
                    "NaverOAuth2TokenResponseClient는 Naver 전용입니다. provider: " + registrationId);
        }

        log.info("========== Naver OAuth2 토큰 응답 처리 시작 ==========");

        try {
            // Naver는 표준 OAuth2와 다른 응답 형식을 사용할 수 있으므로
            // 직접 HTTP 요청을 보내고 응답을 파싱
            String tokenUri = authorizationCodeGrantRequest.getClientRegistration().getProviderDetails().getTokenUri();

            MultiValueMap<String, String> formParameters = new LinkedMultiValueMap<>();
            formParameters.add(OAuth2ParameterNames.GRANT_TYPE,
                    authorizationCodeGrantRequest.getGrantType().getValue());
            formParameters.add(OAuth2ParameterNames.CODE,
                    authorizationCodeGrantRequest.getAuthorizationExchange().getAuthorizationResponse().getCode());
            formParameters.add(OAuth2ParameterNames.REDIRECT_URI,
                    authorizationCodeGrantRequest.getAuthorizationExchange().getAuthorizationResponse()
                            .getRedirectUri());
            formParameters.add(OAuth2ParameterNames.CLIENT_ID,
                    authorizationCodeGrantRequest.getClientRegistration().getClientId());
            formParameters.add(OAuth2ParameterNames.CLIENT_SECRET,
                    authorizationCodeGrantRequest.getClientRegistration().getClientSecret());

            RequestEntity<MultiValueMap<String, String>> requestEntity = RequestEntity
                    .post(tokenUri)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(formParameters);

            ResponseEntity<Map<String, Object>> response = restOperations.exchange(
                    requestEntity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            // 응답 본문 확인
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                log.error("❌ Naver 토큰 응답 본문이 null입니다.");
                throw new RestClientException("Naver 토큰 응답 본문이 null입니다.");
            }

            // 전역 컬렉터에 저장 (토큰 응답 데이터)
            OAuth2DataCollector.saveProviderData("naver_token_response", responseBody);

            // Naver 에러 응답 확인
            if (responseBody.containsKey("error")) {
                String error = (String) responseBody.get("error");
                String errorDescription = (String) responseBody.get("error_description");
                log.error("❌ Naver OAuth2 에러 응답: error={}, error_description={}", error, errorDescription);
                throw new RestClientException(
                        String.format("Naver OAuth2 에러: %s - %s", error, errorDescription));
            }

            // Naver 응답을 OAuth2AccessTokenResponse로 변환
            String accessToken = (String) responseBody.get(OAuth2ParameterNames.ACCESS_TOKEN);
            if (accessToken == null || accessToken.isEmpty()) {
                log.error("❌ Naver 응답에 access_token이 없습니다. 응답 본문: {}", responseBody);
                throw new RestClientException("Naver 응답에 access_token이 없습니다. 응답: " + responseBody);
            }

            OAuth2AccessTokenResponse.Builder builder = OAuth2AccessTokenResponse.withToken(accessToken);

            // 토큰 타입
            String tokenType = (String) responseBody.get(OAuth2ParameterNames.TOKEN_TYPE);
            if (tokenType != null && tokenType.equalsIgnoreCase("bearer")) {
                builder.tokenType(org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER);
            } else {
                builder.tokenType(org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER);
            }

            // 만료 시간
            if (responseBody.containsKey(OAuth2ParameterNames.EXPIRES_IN)) {
                Object expiresIn = responseBody.get(OAuth2ParameterNames.EXPIRES_IN);
                long expiresInSeconds;
                if (expiresIn instanceof Number) {
                    expiresInSeconds = ((Number) expiresIn).longValue();
                } else {
                    expiresInSeconds = Long.parseLong(expiresIn.toString());
                }
                builder.expiresIn(expiresInSeconds);
            }

            // Refresh Token
            if (responseBody.containsKey(OAuth2ParameterNames.REFRESH_TOKEN)) {
                builder.refreshToken((String) responseBody.get(OAuth2ParameterNames.REFRESH_TOKEN));
            }

            // 추가 파라미터
            Map<String, Object> additionalParameters = new LinkedHashMap<>();
            responseBody.forEach((key, value) -> {
                if (!OAuth2ParameterNames.ACCESS_TOKEN.equals(key) &&
                        !OAuth2ParameterNames.TOKEN_TYPE.equals(key) &&
                        !OAuth2ParameterNames.EXPIRES_IN.equals(key) &&
                        !OAuth2ParameterNames.REFRESH_TOKEN.equals(key) &&
                        !OAuth2ParameterNames.SCOPE.equals(key)) {
                    additionalParameters.put(key, value);
                }
            });
            builder.additionalParameters(additionalParameters);

            OAuth2AccessTokenResponse tokenResponse = builder.build();

            return tokenResponse;

        } catch (Exception e) {
            log.error("Naver OAuth2 토큰 응답 처리 실패: {}", e.getMessage(), e);
            throw new RestClientException("Naver 토큰 응답 처리 중 오류 발생: " + e.getMessage(), e);
        }
    }
}
