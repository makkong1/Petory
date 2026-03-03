package com.linkup.Petory.domain.user.controller;

import com.linkup.Petory.domain.user.dto.LoginRequest;
import com.linkup.Petory.domain.user.dto.TokenResponse;
import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.exception.UserValidationException;
import com.linkup.Petory.domain.user.service.AuthService;
import com.linkup.Petory.domain.user.service.EmailVerificationService;
import com.linkup.Petory.domain.user.service.UsersService;
import com.linkup.Petory.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
    private final EmailVerificationService emailVerificationService;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    /**
     * 로그인 API - Access Token과 Refresh Token 발급
     */
    /**
     * [리팩토링] try-catch 제거 → GlobalExceptionHandler로 예외 위임
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest loginRequest) {
        // Spring Security 인증 처리 (id로 인증)
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.id(),
                        loginRequest.password()));

        // AuthService를 통해 로그인 처리 (Access Token + Refresh Token 발급)
        TokenResponse tokenResponse = authService.login(loginRequest.id(), loginRequest.password());

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", tokenResponse.accessToken());
        response.put("refreshToken", tokenResponse.refreshToken());
        response.put("user", tokenResponse.user());
        response.put("message", "로그인 성공");

        log.info("로그인 성공: {}", loginRequest.id());
        return ResponseEntity.ok(response);
    }

    /**
     * 회원가입 API
     */
    /**
     * [리팩토링] try-catch 제거 → GlobalExceptionHandler로 예외 위임
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody UsersDTO userDTO) {
        UsersDTO createdUser = usersService.createUser(userDTO);

        Map<String, Object> response = new HashMap<>();
        response.put("user", createdUser);
        response.put("message", "회원가입 성공");

        log.info("회원가입 성공: {}", createdUser.getUsername());
        return ResponseEntity.ok(response);
    }

    /**
     * Access Token 검증 API
     */
    /**
     * [리팩토링] try-catch 제거 → GlobalExceptionHandler로 예외 위임
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestHeader("Authorization") String authHeader) {
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
    }

    /**
     * Refresh Token으로 Access Token 갱신 API
     */
    /**
     * [리팩토링] try-catch 제거 → GlobalExceptionHandler로 예외 위임
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");

        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Refresh Token이 필요합니다."));
        }

        TokenResponse tokenResponse = authService.refreshAccessToken(refreshToken);

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", tokenResponse.accessToken());
        response.put("refreshToken", tokenResponse.refreshToken());
        response.put("user", tokenResponse.user());
        response.put("message", "토큰 갱신 성공");

        log.info("Access Token 갱신 성공");
        return ResponseEntity.ok(response);
    }

    /**
     * 로그아웃 API - Refresh Token 제거
     */
    /**
     * [리팩토링] try-catch 제거 → GlobalExceptionHandler로 예외 위임
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestHeader("Authorization") String authHeader) {
        String token = jwtUtil.extractTokenFromHeader(authHeader);

        if (token != null && jwtUtil.validateToken(token)) {
            String id = jwtUtil.getIdFromToken(token);
            authService.logout(id);

            log.info("로그아웃 성공: {}", id);
            return ResponseEntity.ok(Map.of("message", "로그아웃 성공"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "유효하지 않은 토큰입니다."));
        }
    }

    /**
     * 비밀번호 찾기 - 비밀번호 재설정 이메일 발송 (인증 불필요)
     */
    /**
     * [리팩토링] try-catch 제거 → GlobalExceptionHandler로 예외 위임
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");

        if (email == null || email.isEmpty()) {
            throw UserValidationException.emailRequired();
        }

        emailVerificationService.sendPasswordResetEmail(email);

        log.info("비밀번호 찾기 이메일 발송 성공: email={}", email);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "비밀번호 재설정 링크가 이메일로 발송되었습니다. 이메일을 확인해주세요."));
    }
}
