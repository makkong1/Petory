package com.linkup.Petory.domain.user.entity;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import com.linkup.Petory.domain.common.BaseTimeEntity;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Users extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Column(nullable = false, unique = true)
    private String id; // 로그인용 아이디

    @Column(nullable = false, unique = true)
    private String username;

    @Column(length = 50, unique = true)
    private String nickname; // 닉네임 (소셜 로그인 사용자 필수 설정)

    @Column(nullable = false, unique = true)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private String password;

    // OAuth2 소셜 로그인 관련 필드
    @Column(name = "profile_image", length = 500)
    private String profileImage; // 프로필 이미지 URL (구글 picture, 네이버 profile_image)

    @Column(name = "birth_date", length = 20)
    private String birthDate; // 생년월일 (네이버: birthyear + birthday 조합, 형식: YYYY-MM-DD)

    @Column(name = "gender", length = 10)
    private String gender; // 성별 (네이버: M/F, 구글: 제공 안 함)

    @Column(name = "email_verified", columnDefinition = "TINYINT(1)")
    private Boolean emailVerified; // 이메일 인증 여부 (구글: email_verified, 네이버: 기본 true)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private String location;

    @Lob
    private String petInfo;

    /** [리팩토링] @BatchSize - socialUsers N+1 제거 (101 쿼리 → 3 쿼리, 100명 기준) */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @BatchSize(size = 50)
    private List<SocialUser> socialUsers;

    // Refresh Token 관련 필드
    private String refreshToken;
    private LocalDateTime refreshExpiration;

    // 통계용: 마지막 로그인 시간
    private LocalDateTime lastLoginAt;

    // 제재 관련 필드
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "warning_count", nullable = false)
    @Builder.Default
    private Integer warningCount = 0;

    @Column(name = "pet_coin_balance", nullable = false)
    @Builder.Default
    private Integer petCoinBalance = 0; // 펫코인 잔액

    @Column(name = "suspended_until")
    private LocalDateTime suspendedUntil; // 이용제한 종료일 (null이면 영구 차단)

    // 소프트 삭제 관련 필드
    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserSanction> sanctions;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Pet> pets; // 등록한 애완동물 목록

    /**
     * 현재 제재 상태인지 확인
     */
    public boolean isSanctioned() {
        if (status == UserStatus.BANNED) {
            return true;
        }
        if (status == UserStatus.SUSPENDED && suspendedUntil != null) {
            return LocalDateTime.now().isBefore(suspendedUntil);
        }
        return false;
    }
}
