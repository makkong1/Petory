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
                    AccountAccess access = resolveAccess(userDetails, request);

                    if (access == AccountAccess.DENY) {
                        log.warn("JWT 인증 거부: 영구 차단 계정 userId={}", id);
                        SecurityContextHolder.clearContext();
                        writeForbidden(response);
                        return;
                    }

                    if (access == AccountAccess.ALLOW) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities());

                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);

                        log.debug("JWT 인증 성공: {}", id);
                    }
                    // ANONYMOUS: SecurityContext를 설정하지 않고 흘려보낸다 — 이후 permitAll 경로는 통과,
                    // 인증이 필요한 경로는 비로그인과 동일하게 401을 받는다(이용제한 = 비로그인 취급).
                }
            }
        } catch (Exception e) {
            log.error("JWT 인증 처리 중 오류 발생: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private enum AccountAccess {
        ALLOW, ANONYMOUS, DENY
    }

    private AccountAccess resolveAccess(UserDetails userDetails, HttpServletRequest request) {
        if (userDetails instanceof CustomUserDetails cud) {
            if (!cud.isAccountNonLocked()) return AccountAccess.DENY;      // BANNED: 항상 거부(비로그인보다도 강하게 차단)
            if (cud.isEnabled()) return AccountAccess.ALLOW;               // ACTIVE: 항상 허용
            if (cud.isSuspensionExpired()) return AccountAccess.ALLOW;     // 만료된 SUSPENDED: 조회 시점 기준 허용
            if (cud.isCurrentlySuspended()) {
                // 신고만 본인 신원을 유지한 채 예외 허용, 그 외엔 비로그인처럼 취급
                return isSuspendedReportException(request) ? AccountAccess.ALLOW : AccountAccess.ANONYMOUS;
            }
            return AccountAccess.ANONYMOUS;
        }
        return (userDetails.isEnabled() && userDetails.isAccountNonLocked()) ? AccountAccess.ALLOW : AccountAccess.DENY;
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
