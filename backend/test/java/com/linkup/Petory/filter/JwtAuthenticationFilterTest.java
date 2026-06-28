package com.linkup.Petory.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.global.security.CustomUserDetails;
import com.linkup.Petory.util.JwtUtil;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserDetailsService userDetailsService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("예외: 영구 차단 사용자의 유효한 access token은 SecurityContext 인증으로 등록되지 않는다")
    void 예외_차단사용자_accessToken_인증거부() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, userDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        Users user = Users.builder()
                .idx(1L)
                .id("banned-user")
                .password("encoded")
                .role(Role.USER)
                .status(UserStatus.BANNED)
                .build();

        when(jwtUtil.extractTokenFromHeader("Bearer access-token")).thenReturn("access-token");
        when(jwtUtil.validateToken("access-token")).thenReturn(true);
        when(jwtUtil.isTokenExpired("access-token")).thenReturn(false);
        when(jwtUtil.getIdFromToken("access-token")).thenReturn("banned-user");
        when(userDetailsService.loadUserByUsername("banned-user")).thenReturn(CustomUserDetails.from(user));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("정상: ACTIVE 사용자의 유효한 access token은 SecurityContext 인증으로 등록된다")
    void 정상_활성사용자_accessToken_인증등록() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, userDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        Users user = Users.builder()
                .idx(1L)
                .id("active-user")
                .password("encoded")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build();

        when(jwtUtil.extractTokenFromHeader("Bearer access-token")).thenReturn("access-token");
        when(jwtUtil.validateToken("access-token")).thenReturn(true);
        when(jwtUtil.isTokenExpired("access-token")).thenReturn(false);
        when(jwtUtil.getIdFromToken("access-token")).thenReturn("active-user");
        when(userDetailsService.loadUserByUsername("active-user")).thenReturn(CustomUserDetails.from(user));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }
}
