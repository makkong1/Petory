package com.linkup.Petory.domain.care.entity;

import com.linkup.Petory.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import com.linkup.Petory.domain.user.entity.Users;

/**
 * 펫케어 리뷰 엔티티
 * 역할: 펫케어 리뷰를 나타내는 엔티티입니다. 펫케어 서비스가 완료된 후, 요청자(reviewer)가 서비스 제공자(reviewee)에게 작성하는 리뷰입니다.
 * 하나의 CareApplication당 1개의 리뷰만 작성 가능하며, ACCEPTED 상태의 CareApplication에 대해서만 리뷰를 작성할 수 있습니다.
 * 평점(1-5)과 리뷰 내용을 포함하며, 제공자의 평균 평점 계산에 사용됩니다.
 */
@Entity
@Table(name = "carereview")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class CareReview extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    /** 리뷰 대상 케어 지원 (ACCEPTED 상태여야 함) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "care_application_idx", nullable = false)
    private CareApplication careApplication;

    /** 리뷰 작성자 (요청자) - 리뷰를 쓴 사람 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_idx", nullable = false)
    private Users reviewer;

    /** 리뷰 대상자 (서비스 제공자) - 리뷰를 받는 사람 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewee_idx", nullable = false)
    private Users reviewee;

    /** 평점 (1~5) */
    @Column(nullable = false)
    private int rating;

    /** 리뷰 내용 */
    @Lob
    private String comment;

}
