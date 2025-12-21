package com.linkup.Petory.global.security;

import com.linkup.Petory.domain.user.handler.OAuth2FailureHandler;
import com.linkup.Petory.domain.user.handler.OAuth2SuccessHandler;
import com.linkup.Petory.domain.user.service.ConditionalOAuth2TokenResponseClient;
import com.linkup.Petory.domain.user.service.OAuth2UserProviderRouter;
import com.linkup.Petory.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity(prePostEnabled = true) // @PreAuthorize 작동 활성화
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    private final OAuth2UserProviderRouter oAuth2UserProviderRouter;
    private final ConditionalOAuth2TokenResponseClient conditionalOAuth2TokenResponseClient;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CORS 설정 (CorsConfig와 연동)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // CSRF 비활성화 (REST API 사용)
                .csrf(csrf -> csrf.disable())

                // 세션을 사용하지 않음 (REST API는 무상태)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // JWT 필터 추가
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // OAuth2 설정
                // Naver는 커스텀 TokenResponseClient 사용, Google은 기본 클라이언트 사용
                .oauth2Login(oauth2 -> oauth2
                        .tokenEndpoint(token -> token
                                .accessTokenResponseClient(conditionalOAuth2TokenResponseClient))
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oAuth2UserProviderRouter))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler))

                // 인증 및 인가가 필요한 경로 설정
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll() // 인증 관련 API 허용
                        .requestMatchers("/api/users/register").permitAll() // 회원가입 허용
                        .requestMatchers(HttpMethod.GET, "/api/users/id/check").permitAll() // 아이디 중복 체크 (회원가입용)
                        .requestMatchers(HttpMethod.GET, "/api/users/nickname/check").permitAll() // 닉네임 중복 체크 (회원가입용)
                        .requestMatchers(HttpMethod.GET, "/api/users/email/verify/**").permitAll() // 이메일 인증 처리 (토큰 기반)
                        .requestMatchers(HttpMethod.POST, "/api/users/email/verify/pre-registration").permitAll() // 회원가입
                        .requestMatchers(HttpMethod.GET, "/api/users/email/verify/pre-registration/check").permitAll() // 회원가입
                        .requestMatchers("/oauth2/**").permitAll() // OAuth2 인증 엔드포인트 허용
                        .requestMatchers(HttpMethod.GET, "/api/uploads/**").permitAll() // 업로드 파일 공개 조회
                        .requestMatchers("/api/geocoding/**").permitAll() // 지오코딩 API 공개 접근 허용
                        .requestMatchers("/error").permitAll() // 에러 페이지
                        .requestMatchers("/ws/**", "/chat/**").permitAll() // WebSocket 엔드포인트 (인증은 인터셉터에서 처리)
                        // MASTER 전용 API - 최상위 권한만 접근 가능
                        .requestMatchers("/api/master/**").hasRole("MASTER")
                        // 관리자 전용 API - ADMIN 또는 MASTER 권한 필요
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "MASTER")
                        // 나머지 API는 인증만 필요 (로그인한 사용자면 가능)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll() // 기타 요청 허용
                )
                // 인증/인가 예외 처리
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            // 401 Unauthorized - 인증되지 않은 사용자
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"error\":\"인증이 필요합니다.\",\"status\":401}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            // 403 Forbidden - 권한이 없는 사용자
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"error\":\"접근 권한이 없습니다.\",\"status\":403}");
                        }));

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // CORS 설정을 위한 CorsConfigurationSource Bean 참조
    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowedOriginPatterns(java.util.Arrays.asList("*")); // 모든 origin 허용
        configuration.setAllowedMethods(java.util.Arrays.asList("*")); // 모든 HTTP 메서드 허용
        configuration.setAllowedHeaders(java.util.Arrays.asList("*")); // 모든 헤더 허용
        configuration.setAllowCredentials(true); // 인증 정보 허용

        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
