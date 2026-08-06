package com.linkup.Petory.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.entity.Provider;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.SocialUser;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.LoginEventRepository;
import com.linkup.Petory.domain.user.repository.SocialUserRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.util.JwtUtil;

@ExtendWith(MockitoExtension.class)
class OAuth2ServiceTest {

    @InjectMocks
    private OAuth2Service oAuth2Service;

    @Mock
    private UsersRepository usersRepository;
    @Mock
    private SocialUserRepository socialUserRepository;
    @Mock
    private UsersService usersService;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private LoginEventRepository loginEventRepository;

    private OAuth2User createMockOAuth2User(String providerId, String email, String name) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", providerId);
        attributes.put("email", email);
        attributes.put("name", name);
        attributes.put("email_verified", true);
        attributes.put("picture", "https://example.com/picture.jpg");

        return new DefaultOAuth2User(
                Collections.singletonList(() -> "sub"),
                attributes,
                "sub");
    }

    @Test
    @DisplayName("정상: 휴면 계정이 OAuth2 로그인에 성공하면 휴면 플래그가 자동 해제된다")
    void 정상_휴면계정_OAuth2로그인시_휴면해제() {
        Users user = Users.builder()
                .id("dormant-oauth-user")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .isDormant(true)
                .dormantAt(LocalDateTime.now().minusDays(1))
                .build();
        SocialUser socialUser = SocialUser.builder()
                .user(user)
                .provider(Provider.GOOGLE)
                .providerId("google-provider-id")
                .build();
        UsersDTO dto = UsersDTO.builder().id("dormant-oauth-user").build();

        when(socialUserRepository.findByProviderAndProviderId(Provider.GOOGLE, "google-provider-id"))
                .thenReturn(Optional.of(socialUser));
        when(jwtUtil.createAccessToken("dormant-oauth-user")).thenReturn("access-token");
        when(jwtUtil.createRefreshToken()).thenReturn("refresh-token");
        when(usersService.getUserById("dormant-oauth-user")).thenReturn(dto);

        OAuth2User oauth2User = createMockOAuth2User("google-provider-id", "dormant@example.com", "Dormant User");

        oAuth2Service.processOAuth2Login(oauth2User, Provider.GOOGLE);

        assertThat(user.getIsDormant()).isFalse();
        assertThat(user.getDormantAt()).isNull();
    }

    @Test
    @DisplayName("예외: 탈퇴한 계정의 소셜 연동이 남아 있어도 OAuth2 로그인은 차단된다")
    void 예외_탈퇴계정_OAuth2로그인_차단() {
        Users user = Users.builder()
                .id("withdrawn_1")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .isDeleted(true)
                .deletedAt(LocalDateTime.now().minusDays(1))
                .build();
        SocialUser socialUser = SocialUser.builder()
                .user(user)
                .provider(Provider.GOOGLE)
                .providerId("google-provider-id")
                .build();

        when(socialUserRepository.findByProviderAndProviderId(Provider.GOOGLE, "google-provider-id"))
                .thenReturn(Optional.of(socialUser));

        OAuth2User oauth2User = createMockOAuth2User("google-provider-id", "withdrawn@example.com", "Withdrawn User");

        assertThatThrownBy(() -> oAuth2Service.processOAuth2Login(oauth2User, Provider.GOOGLE))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("탈퇴한 계정");

        verify(usersRepository, never()).save(any(Users.class));
    }
}
