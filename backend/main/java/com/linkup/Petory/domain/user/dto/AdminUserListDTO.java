package com.linkup.Petory.domain.user.dto;

import java.time.LocalDateTime;

import lombok.Getter;

/**
 * 관리자 사용자 목록 전용 읽기 모델(Read Model).
 *
 * <p>목록 화면(UserList)과 상태관리 모달(UserStatusModal)이 실제로 사용하는 12개 필드만 담는다.
 * 공유 {@code UsersDTO}/{@code UsersConverter}(로그인·프로필·상세 등 20여 곳에서 사용)를 건드리지 않고,
 * JPQL 생성자 표현식 projection으로 필요한 컬럼만 SELECT 하기 위한 목적의 DTO다.
 * (전체 27컬럼 + socialUsers 배치 조회 → 12컬럼 단일 쿼리)
 */
@Getter
public class AdminUserListDTO {

    private final Long idx;
    private final String id;
    private final String nickname;
    private final String username;
    private final String email;
    private final String role;
    private final Boolean isDeleted;
    private final Boolean isDormant;
    private final LocalDateTime createdAt;
    // 상태관리 모달(UserStatusModal)이 목록 행에서 미리 채우는 필드 — 누락 시 모달이 기본값을 표시하는 회귀 발생
    private final String status;
    private final Integer warningCount;
    private final LocalDateTime suspendedUntil;

    public AdminUserListDTO(Long idx, String id, String nickname, String username, String email,
            String role, Boolean isDeleted, Boolean isDormant, LocalDateTime createdAt,
            String status, Integer warningCount, LocalDateTime suspendedUntil) {
        this.idx = idx;
        this.id = id;
        this.nickname = nickname;
        this.username = username;
        this.email = email;
        this.role = role;
        this.isDeleted = isDeleted;
        this.isDormant = isDormant;
        this.createdAt = createdAt;
        this.status = status;
        this.warningCount = warningCount;
        this.suspendedUntil = suspendedUntil;
    }
}
