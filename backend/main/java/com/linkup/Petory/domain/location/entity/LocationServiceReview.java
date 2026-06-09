package com.linkup.Petory.domain.location.entity;

import com.linkup.Petory.domain.common.BaseTimeEntity;
import com.linkup.Petory.domain.user.entity.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "locationservicereview", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"service_idx", "user_idx"})
})
/**
 * 위치 기반 서비스 리뷰 엔티티. 서비스에 대한 별점과 내용을 저장한다.
 */
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

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = java.time.LocalDateTime.now();
    }
}
