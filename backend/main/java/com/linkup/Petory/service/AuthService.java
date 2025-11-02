package com.linkup.Petory.service;

import com.linkup.Petory.dto.TokenResponse;
import com.linkup.Petory.dto.UsersDTO;
import com.linkup.Petory.entity.Users;
import com.linkup.Petory.repository.UsersRepository;
import com.linkup.Petory.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtUtil jwtUtil;
    private final UsersRepository usersRepository;
    private final UsersService usersService;

    /**
     * 로그인 - Access Token과 Refresh Token 발급
     */
    @Transactional
    public TokenResponse login(String id, String password) {
        Users user = usersRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("유저 없음"));

        // Access Token 생성 (15분)
        String accessToken = jwtUtil.createAccessToken(user.getId());

        // Refresh Token 생성 (1일)
        String refreshToken = jwtUtil.createRefreshToken();

        // DB에 refresh token 저장
        user.setRefreshToken(refreshToken);
        user.setRefreshExpiration(LocalDateTime.now().plusDays(1));
        usersRepository.save(user);

        log.info("로그인 성공: {}, Refresh Token 저장 완료", id);

        UsersDTO userDTO = usersService.getUserById(id);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userDTO)
                .build();
    }

    /**
     * Refresh Token으로 Access Token 갱신
     */
    @Transactional
    public TokenResponse refreshAccessToken(String refreshToken) {
        // Refresh Token 유효성 검증
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new RuntimeException("유효하지 않은 Refresh Token");
        }

        // DB에서 Refresh Token 확인
        Users user = usersRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new RuntimeException("Refresh Token을 찾을 수 없습니다"));

        // 만료 시간 확인
        if (user.getRefreshExpiration() == null || 
            user.getRefreshExpiration().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Refresh Token이 만료되었습니다");
        }

        // 새로운 Access Token 생성
        String newAccessToken = jwtUtil.createAccessToken(user.getId());

        log.info("Access Token 갱신 성공: {}", user.getId());

        UsersDTO userDTO = usersService.getUserById(user.getId());

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken) // 기존 Refresh Token 유지
                .user(userDTO)
                .build();
    }

    /**
     * 로그아웃 - Refresh Token 제거 (나중에 Redis로 이동 가능)
     */
    @Transactional
    public void logout(String userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("유저 없음"));

        // DB에서 Refresh Token 제거
        user.setRefreshToken(null);
        user.setRefreshExpiration(null);
        usersRepository.save(user);

        log.info("로그아웃 완료: {}", userId);
    }

    /**
     * Refresh Token 검증
     */
    public boolean validateRefreshToken(String refreshToken) {
        try {
            // JWT 토큰 유효성 검증
            if (!jwtUtil.validateToken(refreshToken)) {
                return false;
            }

            // DB에서 Refresh Token 확인
            Users user = usersRepository.findByRefreshToken(refreshToken)
                    .orElse(null);

            if (user == null) {
                return false;
            }

            // 만료 시간 확인
            if (user.getRefreshExpiration() == null || 
                user.getRefreshExpiration().isBefore(LocalDateTime.now())) {
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("Refresh Token 검증 실패: {}", e.getMessage());
            return false;
        }
    }
}

