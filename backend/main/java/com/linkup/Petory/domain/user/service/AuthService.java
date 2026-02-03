package com.linkup.Petory.domain.user.service;

import com.linkup.Petory.domain.user.dto.TokenResponse;
import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;
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
        Users user = usersRepository.findByIdString(id)
                .orElseThrow(() -> new RuntimeException("유저 없음")); // 1번번

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
                log.info("만료된 이용제한 자동 해제: {}", id);
            }
        }

        // Access Token 생성 (15분)
        String accessToken = jwtUtil.createAccessToken(user.getId());

        // Refresh Token 생성 (1일)
        String refreshToken = jwtUtil.createRefreshToken();

        // DB에 refresh token 저장
        user.setRefreshToken(refreshToken);
        user.setRefreshExpiration(LocalDateTime.now().plusDays(1));
        user.setLastLoginAt(LocalDateTime.now()); // 통계용: 마지막 로그인 시간 업데이트
        usersRepository.save(user); // 2번

        log.info("로그인 성공: {}, Refresh Token 저장 완료", id);

        UsersDTO userDTO = usersService.getUserById(id); // 3번

        return new TokenResponse(accessToken, refreshToken, userDTO);
    }

    /**
     * Refresh Token으로 Access Token 갱신
     */
    @Transactional
    public TokenResponse refreshAccessToken(String refreshToken) {
        log.info("🔄 Access Token 재발급 요청 시작");

        // Refresh Token 유효성 검증
        if (!jwtUtil.validateToken(refreshToken)) {
            log.warn("❌ Refresh Token 유효성 검증 실패");
            throw new RuntimeException("유효하지 않은 Refresh Token");
        }

        // DB에서 Refresh Token 확인
        Users user = usersRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> {
                    log.warn("❌ Refresh Token을 DB에서 찾을 수 없음");
                    return new RuntimeException("Refresh Token을 찾을 수 없습니다");
                });

        // 만료 시간 확인
        if (user.getRefreshExpiration() == null ||
                user.getRefreshExpiration().isBefore(LocalDateTime.now())) {
            log.warn("❌ Refresh Token 만료됨: userId={}, 만료시간={}",
                    user.getId(), user.getRefreshExpiration());
            // 만료된 Refresh Token 삭제
            user.setRefreshToken(null);
            user.setRefreshExpiration(null);
            usersRepository.save(user);
            log.info("🗑️ 만료된 Refresh Token 삭제 완료: userId={}", user.getId());
            throw new RuntimeException("Refresh Token이 만료되었습니다");
        }

        // 새로운 Access Token 생성
        String newAccessToken = jwtUtil.createAccessToken(user.getId());

        log.info("✅ Access Token 재발급 성공: userId={}, 발급시간={}",
                user.getId(), LocalDateTime.now());

        UsersDTO userDTO = usersService.getUserById(user.getId());

        return new TokenResponse(newAccessToken, refreshToken, userDTO);  // 기존 Refresh Token 유지
    }

    /**
     * 로그아웃 - Refresh Token 제거 (나중에 Redis로 이동 가능)
     */
    @Transactional
    public void logout(String userId) {
        Users user = usersRepository.findByIdString(userId)
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
