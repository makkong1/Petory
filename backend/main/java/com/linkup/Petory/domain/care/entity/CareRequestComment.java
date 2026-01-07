package com.linkup.Petory.domain.care.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

import com.linkup.Petory.domain.user.entity.Users;

/**
 * 펫케어 요청 댓글 엔티티
 * 역할: 펫케어 요청 댓글을 나타내는 엔티티입니다. SERVICE_PROVIDER 역할의 사용자가 특정 펫케어 요청에 대해 질문이나 답변을 작성할 때 사용됩니다.
 * 요청자와 서비스 제공자 간의 소통을 위한 기능으로, 댓글 작성 시 요청자에게 알림이 자동으로 발송됩니다.
 * 파일 첨부가 가능하며, Soft Delete 방식으로 삭제됩니다.
 */
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

    private LocalDateTime createdAt;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
