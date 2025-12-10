package com.linkup.Petory.domain.user.service;

import com.linkup.Petory.domain.user.dto.TokenResponse;
import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.entity.Provider;
import com.linkup.Petory.domain.user.service.OAuth2DataCollector;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.SocialUser;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.SocialUserRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2Service {

    private final UsersRepository usersRepository;
    private final SocialUserRepository socialUserRepository;
    private final UsersService usersService;
    private final JwtUtil jwtUtil;

    /**
     * OAuth2 소셜 로그인 처리
     * 
     * @param oauth2User OAuth2 사용자 정보
     * @param provider   소셜 로그인 제공자 (GOOGLE, NAVER)
     * @return TokenResponse (Access Token, Refresh Token, User 정보)
     */
    @Transactional
    public TokenResponse processOAuth2Login(OAuth2User oauth2User, Provider provider) {
        log.info("========== OAuth2 로그인 처리 시작: provider={} ==========", provider);

        // OAuth2User의 전체 attributes 로그 출력 (상세)
        Map<String, Object> attributes = oauth2User.getAttributes();
        log.info("========================================");
        log.info("📋 OAuth2Service에서 받은 전체 Attributes (provider={}, 총 {}개):", provider, attributes.size());
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

        // 전역 컬렉터에 저장 (OAuth2Service에서 받은 최종 데이터)
        OAuth2DataCollector.saveProviderData(provider.name().toLowerCase() + "_oauth2service", attributes);

        // OAuth2 사용자 정보에서 providerId 추출
        String providerId = extractProviderId(oauth2User, provider);
        String email = extractEmail(oauth2User, provider);
        String name = extractName(oauth2User, provider);

        log.info("📌 추출된 정보:");
        log.info("  - providerId: {}", providerId);
        log.info("  - email: {}", email);
        log.info("  - name: {}", name);

        // SocialUser 조회
        Optional<SocialUser> socialUserOpt = socialUserRepository.findByProviderAndProviderId(provider, providerId);

        Users user;

        if (socialUserOpt.isPresent()) {
            // 기존 소셜 로그인 사용자
            SocialUser socialUser = socialUserOpt.get();
            user = socialUser.getUser();
            log.info("기존 소셜 로그인 사용자: userId={}", user.getId());
        } else {
            // 신규 소셜 로그인 사용자 - 회원가입 처리
            user = createOrLinkUser(oauth2User, provider, providerId, email, name);
            log.info("신규 소셜 로그인 사용자 생성: userId={}", user.getId());
        }

        // 제재 상태 확인
        if (user.getStatus() == UserStatus.BANNED) {
            throw new RuntimeException("영구 차단된 계정입니다. 웹사이트 이용이 불가능합니다.");
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            if (user.getSuspendedUntil() != null && user.getSuspendedUntil().isAfter(LocalDateTime.now())) {
                throw new RuntimeException(String.format("이용제한 중인 계정입니다. 해제일: %s",
                        user.getSuspendedUntil().toString()));
            } else {
                // 만료된 이용제한 자동 해제
                user.setStatus(UserStatus.ACTIVE);
                user.setSuspendedUntil(null);
                usersRepository.save(user);
                log.info("만료된 이용제한 자동 해제: {}", user.getId());
            }
        }

        // Access Token 생성
        String accessToken = jwtUtil.createAccessToken(user.getId());

        // Refresh Token 생성
        String refreshToken = jwtUtil.createRefreshToken();

        // DB에 refresh token 저장
        user.setRefreshToken(refreshToken);
        user.setRefreshExpiration(LocalDateTime.now().plusDays(1));
        user.setLastLoginAt(LocalDateTime.now());
        usersRepository.save(user);

        UsersDTO userDTO = usersService.getUserById(user.getId());

        log.info("✅ OAuth2 로그인 성공:");
        log.info("  - userId: {}", user.getId());
        log.info("  - provider: {}", provider);
        log.info("  - username: {}", user.getUsername());
        log.info("  - email: {}", user.getEmail());
        log.info("========== OAuth2 로그인 처리 완료 ==========");

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userDTO)
                .build();
    }

    /**
     * Provider별로 providerId 추출
     * 각 provider별 OAuth2UserService에서 이미 표준화된 형태로 변환했으므로
     * 일관된 방식으로 처리 가능
     */
    private String extractProviderId(OAuth2User oauth2User, Provider provider) {
        Map<String, Object> attributes = oauth2User.getAttributes();

        return switch (provider) {
            case GOOGLE -> (String) attributes.get("sub");
            case NAVER -> (String) attributes.get("id"); // NaverOAuth2UserService에서 이미 response를 attributes로 변환
            default -> throw new IllegalArgumentException("지원하지 않는 Provider입니다: " + provider);
        };
    }

    /**
     * Provider별로 email 추출
     */
    private String extractEmail(OAuth2User oauth2User, Provider provider) {
        Map<String, Object> attributes = oauth2User.getAttributes();
        // 각 provider별 서비스에서 이미 표준화했으므로 동일한 방식으로 처리
        return (String) attributes.get("email");
    }

    /**
     * Provider별로 name 추출
     */
    private String extractName(OAuth2User oauth2User, Provider provider) {
        Map<String, Object> attributes = oauth2User.getAttributes();
        // 각 provider별 서비스에서 이미 표준화했으므로 동일한 방식으로 처리
        return (String) attributes.get("name");
    }

    /**
     * 신규 소셜 로그인 사용자 생성 또는 기존 사용자와 연결
     */
    @Transactional
    private Users createOrLinkUser(OAuth2User oauth2User, Provider provider, String providerId, String email,
            String name) {
        // 이메일로 기존 사용자 확인
        Optional<Users> existingUserOpt = usersRepository.findByEmail(email);

        Users user;

        if (existingUserOpt.isPresent()) {
            // 기존 사용자가 있으면 소셜 계정 연결
            user = existingUserOpt.get();
            log.info("기존 사용자에 소셜 계정 연결: userId={}, provider={}", user.getId(), provider);
        } else {
            // 신규 사용자 생성
            String uniqueId = generateUniqueId(provider, providerId);
            String uniqueUsername = generateUniqueUsername(name, email);

            user = Users.builder()
                    .id(uniqueId)
                    .username(uniqueUsername)
                    .email(email)
                    .password(UUID.randomUUID().toString()) // 소셜 로그인은 비밀번호 불필요
                    .role(Role.USER)
                    .status(UserStatus.ACTIVE)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            user = usersRepository.save(user);
            log.info("신규 소셜 로그인 사용자 생성: userId={}, email={}", user.getId(), email);
        }

        // SocialUser 생성 및 저장
        SocialUser socialUser = SocialUser.builder()
                .user(user)
                .provider(provider)
                .providerId(providerId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        socialUserRepository.save(socialUser);
        log.info("SocialUser 저장 완료: provider={}, providerId={}", provider, providerId);

        return user;
    }

    /**
     * 고유한 ID 생성 (provider_providerId 형식)
     */
    private String generateUniqueId(Provider provider, String providerId) {
        String baseId = provider.name().toLowerCase() + "_" + providerId;
        String uniqueId = baseId;
        int suffix = 1;

        while (usersRepository.findByIdString(uniqueId).isPresent()) {
            uniqueId = baseId + "_" + suffix;
            suffix++;
        }

        return uniqueId;
    }

    /**
     * 고유한 username 생성
     */
    private String generateUniqueUsername(String name, String email) {
        String baseUsername = name != null && !name.isEmpty() ? name : email.split("@")[0];
        String uniqueUsername = baseUsername;
        int suffix = 1;

        while (usersRepository.findByUsername(uniqueUsername).isPresent()) {
            uniqueUsername = baseUsername + "_" + suffix;
            suffix++;
        }

        return uniqueUsername;
    }
}
