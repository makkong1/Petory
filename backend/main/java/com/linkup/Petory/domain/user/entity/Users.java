package com.linkup.Petory.domain.user.entity;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Column(nullable = false, unique = true)
    private String id; // 로그인용 아이디

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private String location;

    @Lob
    private String petInfo;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
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

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = UserStatus.ACTIVE;
        }
        if (this.warningCount == null) {
            this.warningCount = 0;
        }
        if (this.isDeleted == null) {
            this.isDeleted = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum UserStatus {
        ACTIVE,      // 정상
        SUSPENDED,   // 이용제한 중
        BANNED       // 영구 차단
    }

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
