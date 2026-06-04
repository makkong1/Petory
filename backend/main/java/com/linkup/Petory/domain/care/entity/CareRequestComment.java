package com.linkup.Petory.domain.care.entity;

import com.linkup.Petory.domain.common.BaseTimeEntity;
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
public class CareRequestComment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    /** 댓글이 달린 펫케어 요청 */
    @ManyToOne
    @JoinColumn(name = "care_request_idx", nullable = false)
    private CareRequest careRequest;

    /** 댓글 작성자 (SERVICE_PROVIDER만 작성 가능) */
    @ManyToOne
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user;

    /** 댓글 내용 */
    @Lob
    private String content;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    public void restore() {
        this.isDeleted = false;
        this.deletedAt = null;
    }
}
