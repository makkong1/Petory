package com.linkup.Petory.controller;

import com.linkup.Petory.dto.LoginRequest;
import com.linkup.Petory.dto.UsersDTO;
import com.linkup.Petory.service.UsersService;
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

    private final UsersService usersService;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    /**
     * 로그인 API
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest loginRequest) {
        try {
            // Spring Security 인증 처리 (id로 인증)
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getId(),
                            loginRequest.getPassword()));

            // JWT 토큰 생성 (id를 subject로 사용)
            String token = jwtUtil.generateToken(loginRequest.getId());

            // 사용자 정보 조회
            UsersDTO user = usersService.getUserById(loginRequest.getId());

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("user", user);
            response.put("message", "로그인 성공");

            log.info("로그인 성공: {}", loginRequest.getId());
            return ResponseEntity.ok(response);

        } catch (AuthenticationException e) {
            log.error("로그인 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", "아이디 또는 비밀번호가 올바르지 않습니다.");
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
     * 토큰 검증 API
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
}
