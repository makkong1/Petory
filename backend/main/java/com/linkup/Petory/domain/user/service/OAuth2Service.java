package com.linkup.Petory.domain.user.service;

import com.linkup.Petory.domain.user.dto.TokenResponse;
import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.entity.Provider;
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
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2Service {

    private final UsersRepository usersRepository;
    private final SocialUserRepository socialUserRepository;
    private final UsersService usersService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            case NAVER, KAKAO -> (String) attributes.get("id"); // Naver, Kakao는 id가 식별자
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

        Map<String, Object> attributes = oauth2User.getAttributes();
        boolean isNewUser = false;

        if (existingUserOpt.isPresent()) {
            // 기존 사용자가 있으면 소셜 계정 연결
            user = existingUserOpt.get();
            log.info("기존 사용자에 소셜 계정 연결: userId={}, provider={}", user.getId(), provider);

            // 기존 사용자도 소셜 데이터로 업데이트 (없는 필드만)
            updateUserWithSocialData(user, attributes, provider);

            // 기존 사용자의 nickname이 없으면 설정 필요 (null 유지, 프론트에서 설정하도록)
            // nickname은 사용자가 직접 설정해야 함
        } else {
            // 신규 사용자 생성
            isNewUser = true;
            String uniqueId = generateUniqueId(provider, providerId);
            String uniqueUsername = generateUniqueUsername(name, email);

            user = Users.builder()
                    .id(uniqueId)
                    .username(uniqueUsername)
                    .nickname(null) // 소셜 로그인 사용자는 처음에 닉네임 없음 (설정 필요)
                    .email(email)
                    .password(UUID.randomUUID().toString()) // 소셜 로그인은 비밀번호 불필요
                    .role(Role.USER)
                    .status(UserStatus.ACTIVE)

                    .build();

            // 소셜 데이터로 사용자 정보 설정
            setUserSocialData(user, attributes, provider);

            user = usersRepository.save(user);
            log.info("신규 소셜 로그인 사용자 생성: userId={}, email={}", user.getId(), email);
        }

        // SocialUser 생성 또는 업데이트
        Optional<SocialUser> existingSocialUserOpt = socialUserRepository.findByProviderAndProviderId(provider,
                providerId);
        SocialUser socialUser;

        if (existingSocialUserOpt.isPresent()) {
            // 기존 SocialUser 업데이트
            socialUser = existingSocialUserOpt.get();
            log.info("기존 SocialUser 업데이트: provider={}, providerId={}", provider, providerId);
        } else {
            // 신규 SocialUser 생성
            socialUser = SocialUser.builder()
                    .user(user)
                    .provider(provider)
                    .providerId(providerId)

                    .build();
        }

        // SocialUser에 Provider별 상세 정보 저장
        setSocialUserProviderData(socialUser, attributes, provider);

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

    /**
     * Users 엔티티에 소셜 로그인 데이터 설정 (신규 사용자용)
     */
    private void setUserSocialData(Users user, Map<String, Object> attributes, Provider provider) {
        switch (provider) {
            case GOOGLE -> {
                // Google 데이터 추출
                user.setProfileImage((String) attributes.get("picture"));
                user.setEmailVerified((Boolean) attributes.get("email_verified"));
                // Google은 birth_date, gender 제공 안 함
            }
            case NAVER -> {
                // Naver 데이터 추출
                user.setProfileImage((String) attributes.get("profile_image"));
                user.setEmailVerified(true); // Naver는 기본적으로 이메일 인증됨

                // 생년월일 조합 (birthyear + birthday)
                String birthyear = (String) attributes.get("birthyear");
                String birthday = (String) attributes.get("birthday");
                if (birthyear != null && birthday != null) {
                    // birthday 형식: MM-DD -> YYYY-MM-DD로 변환
                    String birthDate = birthyear + "-" + birthday;
                    user.setBirthDate(birthDate);
                }

                // 성별
                user.setGender((String) attributes.get("gender"));
            }
            case KAKAO -> {
                // Kakao 데이터 추출
                user.setProfileImage((String) attributes.get("profile_image"));
                user.setEmailVerified(true); // Kakao 이메일 있으면 인증된 것으로 간주 (설정에 따라 다름)

                // 생년월일
                String birthyear = (String) attributes.get("birthyear");
                String birthday = (String) attributes.get("birthday"); // MMDD
                if (birthyear != null && birthday != null) {
                    user.setBirthDate(birthyear + "-" + birthday.substring(0, 2) + "-" + birthday.substring(2));
                }

                // 성별
                user.setGender((String) attributes.get("gender"));
            }
        }
    }

    /**
     * 기존 Users 엔티티에 소셜 로그인 데이터 업데이트 (없는 필드만)
     */
    private void updateUserWithSocialData(Users user, Map<String, Object> attributes, Provider provider) {
        switch (provider) {
            case GOOGLE -> {
                // 프로필 이미지가 없으면 설정
                if (user.getProfileImage() == null) {
                    user.setProfileImage((String) attributes.get("picture"));
                }
                // 이메일 인증 여부가 없으면 설정
                if (user.getEmailVerified() == null) {
                    user.setEmailVerified((Boolean) attributes.get("email_verified"));
                }
            }
            case NAVER -> {
                // 프로필 이미지가 없으면 설정
                if (user.getProfileImage() == null) {
                    user.setProfileImage((String) attributes.get("profile_image"));
                }
                // 이메일 인증 여부가 없으면 설정
                if (user.getEmailVerified() == null) {
                    user.setEmailVerified(true);
                }
                // 생년월일이 없으면 설정
                if (user.getBirthDate() == null) {
                    String birthyear = (String) attributes.get("birthyear");
                    String birthday = (String) attributes.get("birthday");
                    if (birthyear != null && birthday != null) {
                        user.setBirthDate(birthyear + "-" + birthday);
                    }
                }
                // 성별이 없으면 설정
                if (user.getGender() == null) {
                    user.setGender((String) attributes.get("gender"));
                }
            }
            case KAKAO -> {
                if (user.getProfileImage() == null) {
                    user.setProfileImage((String) attributes.get("profile_image"));
                }
                if (user.getEmailVerified() == null) {
                    user.setEmailVerified(true);
                }
                if (user.getGender() == null) {
                    user.setGender((String) attributes.get("gender"));
                }
            }
        }
    }

    /**
     * SocialUser 엔티티에 Provider별 상세 정보 저장
     */
    private void setSocialUserProviderData(SocialUser socialUser, Map<String, Object> attributes, Provider provider) {
        try {
            // Provider별 원본 데이터를 JSON으로 저장
            String providerDataJson = objectMapper.writeValueAsString(attributes);
            socialUser.setProviderData(providerDataJson);
        } catch (Exception e) {
            log.warn("Provider 데이터 JSON 변환 실패: {}", e.getMessage());
        }

        switch (provider) {
            case GOOGLE -> {
                // Google 데이터 추출
                socialUser.setProviderProfileImage((String) attributes.get("picture"));

                // 이름 조합 (given_name + family_name)
                String givenName = (String) attributes.get("given_name");
                String familyName = (String) attributes.get("family_name");
                if (givenName != null || familyName != null) {
                    String fullName = (givenName != null ? givenName : "") +
                            (familyName != null ? " " + familyName : "");
                    socialUser.setProviderName(fullName.trim());
                }
                // Google은 전화번호, 나이대 제공 안 함
            }
            case NAVER -> {
                // Naver 데이터 추출
                socialUser.setProviderProfileImage((String) attributes.get("profile_image"));
                socialUser.setProviderName((String) attributes.get("name"));

                // 전화번호 (mobile_e164 우선, 없으면 mobile)
                String phone = (String) attributes.get("mobile_e164");
                if (phone == null || phone.isEmpty()) {
                    phone = (String) attributes.get("mobile");
                }
                socialUser.setProviderPhone(phone);

                // 나이대
                socialUser.setProviderAgeRange((String) attributes.get("age"));
            }
            case KAKAO -> {
                socialUser.setProviderProfileImage((String) attributes.get("profile_image"));
                socialUser.setProviderName((String) attributes.get("nickname"));
                socialUser.setProviderPhone((String) attributes.get("phone_number"));
                socialUser.setProviderAgeRange((String) attributes.get("age_range"));
            }
        }
    }
}
