package com.linkup.Petory.domain.care.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

import com.linkup.Petory.domain.common.ContentStatus;
import com.linkup.Petory.domain.user.entity.Users;

@Entity
@Table(name = "carerequest_comment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareRequestComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne
    @JoinColumn(name = "care_request_idx", nullable = false)
    private CareRequest careRequest;

    @ManyToOne
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user;

    @Lob
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ContentStatus status = ContentStatus.ACTIVE;

    private LocalDateTime createdAt;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = ContentStatus.ACTIVE;
        }
    }
}
