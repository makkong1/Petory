package com.linkup.Petory.util;

import com.linkup.Petory.domain.user.entity.EmailVerificationPurpose;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}") // 24시간 (밀리초)
    private long expiration;

    private static final long ACCESS_TOKEN_EXPIRE_TIME = 15 * 60 * 1000L; // 15분 (짧게 설정)
    private static final long REFRESH_TOKEN_EXPIRE_TIME = 24 * 60 * 60 * 1000L; // 1일
    private static final long EMAIL_VERIFICATION_TOKEN_EXPIRE_TIME = 24 * 60 * 60 * 1000L; // 24시간

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * JWT 토큰 생성
     */
    public String generateToken(String id) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(id) // id를 subject로 저장
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    // Access Token 생성 (id 기준)
    public String createAccessToken(String id) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + ACCESS_TOKEN_EXPIRE_TIME);

        return Jwts.builder()
                .subject(id)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    // Refresh Token 생성
    public String createRefreshToken() {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + REFRESH_TOKEN_EXPIRE_TIME);

        return Jwts.builder()
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * JWT 토큰에서 id 추출
     */
    public String getIdFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException e) {
            log.error("JWT 토큰에서 id 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * JWT 토큰에서 username 추출 (하위 호환성을 위해 유지)
     * 
     * @deprecated getIdFromToken을 사용하세요
     */
    @Deprecated
    public String getUsernameFromToken(String token) {
        return getIdFromToken(token);
    }

    /**
     * JWT 토큰 유효성 검증
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT 토큰 검증 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * JWT 토큰 만료 여부 확인
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getExpiration().before(new Date());
        } catch (JwtException e) {
            log.error("JWT 토큰 만료 확인 실패: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Authorization 헤더에서 토큰 추출
     */
    public String extractTokenFromHeader(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        return null;
    }

    /**
     * 이메일 인증 토큰 생성
     * 
     * @param userId  사용자 ID (또는 이메일 - 회원가입 전용)
     * @param purpose 인증 용도
     * @return 이메일 인증 토큰
     */
    public String createEmailVerificationToken(String userId, EmailVerificationPurpose purpose) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EMAIL_VERIFICATION_TOKEN_EXPIRE_TIME);

        return Jwts.builder()
                .subject(userId)
                .claim("purpose", purpose.name())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 이메일 기반 임시 인증 토큰 생성 (회원가입 전용)
     * 
     * @param email   이메일 주소
     * @param purpose 인증 용도
     * @return 이메일 인증 토큰
     */
    public String createEmailVerificationTokenByEmail(String email, EmailVerificationPurpose purpose) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EMAIL_VERIFICATION_TOKEN_EXPIRE_TIME);

        return Jwts.builder()
                .subject(email) // 이메일을 subject로 사용
                .claim("purpose", purpose.name())
                .claim("isPreRegistration", true) // 회원가입 전 인증임을 표시
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 이메일 인증 토큰에서 이메일 추출 (회원가입 전용)
     * 
     * @param token 이메일 인증 토큰
     * @return 이메일 주소
     */
    public String extractEmailFromEmailToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Boolean isPreRegistration = claims.get("isPreRegistration", Boolean.class);
            if (Boolean.TRUE.equals(isPreRegistration)) {
                return claims.getSubject(); // 회원가입 전 인증이면 subject가 이메일
            }
            return null;
        } catch (JwtException e) {
            log.error("이메일 인증 토큰에서 이메일 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 이메일 인증 토큰에서 사용자 ID 추출
     * 
     * @param token 이메일 인증 토큰
     * @return 사용자 ID
     */
    public String extractUserIdFromEmailToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException e) {
            log.error("이메일 인증 토큰에서 사용자 ID 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 이메일 인증 토큰에서 용도(Purpose) 추출
     * 
     * @param token 이메일 인증 토큰
     * @return 인증 용도
     */
    public EmailVerificationPurpose extractPurposeFromEmailToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String purposeStr = claims.get("purpose", String.class);
            return EmailVerificationPurpose.valueOf(purposeStr);
        } catch (JwtException | IllegalArgumentException e) {
            log.error("이메일 인증 토큰에서 용도 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 이메일 인증 토큰 유효성 검증
     * 
     * @param token 이메일 인증 토큰
     * @return 유효 여부
     */
    public boolean validateEmailVerificationToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("이메일 인증 토큰 검증 실패: {}", e.getMessage());
            return false;
        }
    }
}
