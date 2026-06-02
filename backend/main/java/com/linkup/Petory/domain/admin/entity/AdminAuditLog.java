package com.linkup.Petory.domain.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_audit_log",
    indexes = @Index(name = "idx_audit_admin_created", columnList = "admin_idx, created_at"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
/** 관리자 행위 감사 로그. 어떤 관리자가 어떤 대상에 어떤 작업을 했는지 기록한다. */
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Column(name = "admin_idx", nullable = false)
    private Long adminIdx;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "target_type", length = 30)
    private String targetType;

    @Column(name = "target_idx")
    private Long targetIdx;

    @Column(name = "detail", length = 500)
    private String detail;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
