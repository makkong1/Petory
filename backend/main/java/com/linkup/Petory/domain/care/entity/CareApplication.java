package com.linkup.Petory.domain.care.entity;

import com.linkup.Petory.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import com.linkup.Petory.domain.user.entity.Users;

@Entity
@Table(name = "careapplication")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class CareApplication extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "care_request_idx", nullable = false)
    private CareApplication careRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_idx", nullable = false)
    private Users provider; // 케어 제공자

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CareApplicationStatus status = CareApplicationStatus.PENDING;

    @Lob
    private String message;

}
