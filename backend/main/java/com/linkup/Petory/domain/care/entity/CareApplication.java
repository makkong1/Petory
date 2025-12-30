package com.linkup.Petory.domain.care.entity;

import com.linkup.Petory.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import com.linkup.Petory.domain.user.entity.Users;

/**
 * 펫케어 지원 엔티티
 * 역할: 펫케어 지원을 나타내는 엔티티입니다. 서비스 제공자(SERVICE_PROVIDER)가 특정 펫케어 요청에 지원할 때 생성됩니다.
 * 상태는 PENDING → ACCEPTED 또는 REJECTED로 변경되며, ACCEPTED 상태가 되면 실제 서비스가 시작됩니다.
 * 채팅을 통한 거래 확정 시 양쪽 모두 확정하면 자동으로 ACCEPTED 상태가 되고, 요청 상태가 IN_PROGRESS로 변경됩니다.
 * 하나의 요청에는 여러 지원이 가능하지만, 일반적으로 1명만 ACCEPTED 상태가 됩니다.
 */
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
    private CareRequest careRequest;

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
