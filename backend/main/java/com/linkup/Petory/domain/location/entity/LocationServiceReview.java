package com.linkup.Petory.domain.location.entity;

import jakarta.persistence.*;
import lombok.*;
import com.linkup.Petory.domain.common.BaseTimeEntity;
import com.linkup.Petory.domain.user.entity.Users;

@Entity
@Table(name = "locationservicereview")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationServiceReview extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne
    @JoinColumn(name = "service_idx", nullable = false)
    private LocationService service;

    @ManyToOne
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user;

    @Column(nullable = false)
    private Integer rating; // 평점 (1~5)

    @Lob
    private String comment; // 리뷰 내용

    // Soft Delete 필드
    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;

    // createdAt, updatedAt은 BaseTimeEntity에서 상속받음
    // @PrePersist, @PreUpdate는 BaseTimeEntity의 @EntityListeners가 자동 처리
}
