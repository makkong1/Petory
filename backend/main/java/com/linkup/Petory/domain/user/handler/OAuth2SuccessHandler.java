package com.linkup.Petory.domain.user.handler;

import com.linkup.Petory.domain.user.dto.TokenResponse;
import com.linkup.Petory.domain.user.entity.Provider;
import com.linkup.Petory.domain.user.service.OAuth2Service;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuth2Service oAuth2Service;

    @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth2/callback}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        log.info("OAuth2 인증 성공");

        OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oauth2Token.getPrincipal();
        String registrationId = oauth2Token.getAuthorizedClientRegistrationId();

        log.info("OAuth2 registrationId: {}", registrationId);

        Provider provider = Provider.valueOf(registrationId.toUpperCase());

        try {
            // OAuth2 로그인 처리
            TokenResponse tokenResponse = oAuth2Service.processOAuth2Login(oAuth2User, provider);

            // 닉네임이 없으면 닉네임 설정 페이지로 리다이렉트
            boolean needsNickname = tokenResponse.getUser().getNickname() == null ||
                    tokenResponse.getUser().getNickname().trim().isEmpty();

            String targetUrl;
            if (needsNickname) {
                // 닉네임 설정 페이지로 리다이렉트
                targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                        .queryParam("accessToken",
                                URLEncoder.encode(tokenResponse.getAccessToken(), StandardCharsets.UTF_8))
                        .queryParam("refreshToken",
                                URLEncoder.encode(tokenResponse.getRefreshToken(), StandardCharsets.UTF_8))
                        .queryParam("success", "true")
                        .queryParam("needsNickname", "true")
                        .build()
                        .toUriString();
                log.info("OAuth2 로그인 성공, 닉네임 설정 필요: {}", targetUrl);
            } else {
                // 일반 로그인 성공 페이지로 리다이렉트
                targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                        .queryParam("accessToken",
                                URLEncoder.encode(tokenResponse.getAccessToken(), StandardCharsets.UTF_8))
                        .queryParam("refreshToken",
                                URLEncoder.encode(tokenResponse.getRefreshToken(), StandardCharsets.UTF_8))
                        .queryParam("success", "true")
                        .build()
                        .toUriString();
                log.info("OAuth2 로그인 성공, 리다이렉트: {}", targetUrl);
            }

            getRedirectStrategy().sendRedirect(request, response, targetUrl);

        } catch (Exception e) {
            log.error("OAuth2 로그인 처리 실패: {}", e.getMessage(), e);
            String errorUrl = UriComponentsBuilder.fromUriString(redirectUri)
                    .queryParam("error", URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8))
                    .queryParam("success", "false")
                    .build()
                    .toUriString();
            getRedirectStrategy().sendRedirect(request, response, errorUrl);
        }
    }

}
