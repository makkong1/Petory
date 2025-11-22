package com.linkup.Petory.filter;

import com.linkup.Petory.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
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

            if (token != null && jwtUtil.validateToken(token)) {
                String id = jwtUtil.getIdFromToken(token);

                if (id != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(id);

                    if (jwtUtil.validateToken(token) && !jwtUtil.isTokenExpired(token)) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities());

                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);

                        log.debug("JWT 인증 성공: {}", id);
                    }
                }
            }
        } catch (Exception e) {
            log.error("JWT 인증 처리 중 오류 발생: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
