package com.linkup.Petory.filter;

import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.linkup.Petory.global.security.CustomUserDetails;
import com.linkup.Petory.util.JwtUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * JWT 인증 필터. 요청마다 Authorization 헤더의 토큰을 검증하고 SecurityContext에 인증 정보를 설정한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = null;

            // 1. 헤더에서 토큰 추출 (일반 요청)
            String authorizationHeader = request.getHeader("Authorization");
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                token = jwtUtil.extractTokenFromHeader(authorizationHeader);
            }

            // 2. 쿼리 파라미터에서 토큰 추출 (SSE 등 헤더를 사용할 수 없는 경우)
            if (token == null) {
                token = request.getParameter("token");
            }

            if (token != null && jwtUtil.validateToken(token) && !jwtUtil.isTokenExpired(token)) {
                String id = jwtUtil.getIdFromToken(token);

                if (id != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(id);
                    if (!isUsableAccount(userDetails, request)) {
                        log.warn("JWT 인증 거부: 제재 또는 비활성 계정 userId={}", id);
                        SecurityContextHolder.clearContext();
                        writeForbidden(response);
                        return;
                    }

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    log.debug("JWT 인증 성공: {}", id);
                }
            }
        } catch (Exception e) {
            log.error("JWT 인증 처리 중 오류 발생: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private boolean isUsableAccount(UserDetails userDetails, HttpServletRequest request) {
        if (userDetails instanceof CustomUserDetails cud) {
            if (!cud.isAccountNonLocked()) return false;  // BANNED: 항상 거부
            if (cud.isEnabled()) return true;             // ACTIVE: 항상 허용
            // SUSPENDED인 경우: POST /api/reports만 예외 허용
            if (cud.isCurrentlySuspended()) {
                return isSuspendedReportException(request);
            }
            return false;
        }
        return userDetails.isEnabled() && userDetails.isAccountNonLocked();
    }

    // POST /api/reports 예외: SUSPENDED 사용자가 신고를 생성할 수 있는 유일한 경로
    private boolean isSuspendedReportException(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/api/reports".equals(request.getServletPath());
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"제재 상태에서는 접근할 수 없습니다.\",\"status\":403}");
    }
}
