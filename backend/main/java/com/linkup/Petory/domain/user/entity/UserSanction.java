package com.linkup.Petory.domain.user.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_sanctions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSanction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SanctionType sanctionType;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "duration_days")
    private Integer durationDays; // null이면 영구

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt; // null이면 영구

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_idx")
    private Users admin; // 처리한 관리자

    @Column(name = "report_idx")
    private Long reportIdx; // 관련 신고 ID (nullable)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.startsAt == null) {
            this.startsAt = LocalDateTime.now();
        }
    }

    public enum SanctionType {
        WARNING,      // 경고
        SUSPENSION,   // 이용제한 (일시적)
        BAN          // 영구 차단
    }

    /**
     * 제재가 현재 유효한지 확인
     */
    public boolean isActive() {
        if (endsAt == null) {
            // 영구 제재
            return sanctionType == SanctionType.BAN;
        }
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(startsAt) && now.isBefore(endsAt);
    }
}

