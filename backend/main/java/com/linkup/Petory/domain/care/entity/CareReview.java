package com.linkup.Petory.domain.care.entity;

import com.linkup.Petory.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import com.linkup.Petory.domain.user.entity.Users;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "care_application_idx", nullable = false)
    private CareApplication careApplication;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_idx", nullable = false)
    private Users reviewer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewee_idx", nullable = false)
    private Users reviewee;

    @Column(nullable = false)
    private int rating;

    @Lob
    private String comment;

}
