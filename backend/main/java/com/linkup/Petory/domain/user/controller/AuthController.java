package com.linkup.Petory.domain.user.controller;

import com.linkup.Petory.domain.user.dto.LoginRequest;
import com.linkup.Petory.domain.user.dto.TokenResponse;
import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.service.AuthService;
import com.linkup.Petory.domain.user.service.UsersService;
import com.linkup.Petory.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UsersService usersService;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    /**
     * 로그인 API - Access Token과 Refresh Token 발급
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest loginRequest) {
        try {
            // Spring Security 인증 처리 (id로 인증)
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getId(),
                            loginRequest.getPassword()));

            // AuthService를 통해 로그인 처리 (Access Token + Refresh Token 발급)
            // 내부에서 제재 체크도 수행
            TokenResponse tokenResponse = authService.login(loginRequest.getId(), loginRequest.getPassword());

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", tokenResponse.getAccessToken());
            response.put("refreshToken", tokenResponse.getRefreshToken());
            response.put("user", tokenResponse.getUser());
            response.put("message", "로그인 성공");

            log.info("tokenResponse: {}", response);

            log.info("로그인 성공: {}", loginRequest.getId());
            return ResponseEntity.ok(response);

        } catch (AuthenticationException e) {
            log.error("로그인 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", "아이디 또는 비밀번호가 올바르지 않습니다.");
            return ResponseEntity.badRequest().body(response);
        } catch (RuntimeException e) {
            log.error("로그인 처리 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 회원가입 API
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody UsersDTO userDTO) {
        try {
            UsersDTO createdUser = usersService.createUser(userDTO);

            Map<String, Object> response = new HashMap<>();
            response.put("user", createdUser);
            response.put("message", "회원가입 성공");

            log.info("회원가입 성공: {}", createdUser.getUsername());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("회원가입 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", "회원가입 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Access Token 검증 API
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = jwtUtil.extractTokenFromHeader(authHeader);

            if (token != null && jwtUtil.validateToken(token)) {
                String id = jwtUtil.getIdFromToken(token);
                UsersDTO user = usersService.getUserById(id);

                Map<String, Object> response = new HashMap<>();
                response.put("valid", true);
                response.put("user", user);
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("valid", false);
                response.put("error", "유효하지 않은 토큰입니다.");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("토큰 검증 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("valid", false);
            response.put("error", "토큰 검증 중 오류가 발생했습니다.");
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Refresh Token으로 Access Token 갱신 API
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(@RequestBody Map<String, String> request) {
        try {
            String refreshToken = request.get("refreshToken");

            if (refreshToken == null || refreshToken.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("error", "Refresh Token이 필요합니다.");
                return ResponseEntity.badRequest().body(response);
            }

            // Refresh Token으로 Access Token 갱신
            TokenResponse tokenResponse = authService.refreshAccessToken(refreshToken);

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", tokenResponse.getAccessToken());
            response.put("refreshToken", tokenResponse.getRefreshToken());
            response.put("user", tokenResponse.getUser());
            response.put("message", "토큰 갱신 성공");

            log.info("Access Token 갱신 성공");
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("토큰 갱신 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("토큰 갱신 중 오류 발생: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", "토큰 갱신 중 오류가 발생했습니다.");
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 로그아웃 API - Refresh Token 제거
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = jwtUtil.extractTokenFromHeader(authHeader);

            if (token != null && jwtUtil.validateToken(token)) {
                String id = jwtUtil.getIdFromToken(token);
                authService.logout(id);

                Map<String, Object> response = new HashMap<>();
                response.put("message", "로그아웃 성공");
                log.info("로그아웃 성공: {}", id);
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("error", "유효하지 않은 토큰입니다.");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("로그아웃 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", "로그아웃 중 오류가 발생했습니다.");
            return ResponseEntity.badRequest().body(response);
        }
    }
}
