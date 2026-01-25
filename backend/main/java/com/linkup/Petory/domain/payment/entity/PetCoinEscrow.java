package com.linkup.Petory.domain.payment.entity;

import com.linkup.Petory.domain.care.entity.CareApplication;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.common.BaseTimeEntity;
import com.linkup.Petory.domain.user.entity.Users;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 펫코인 에스크로 엔티티
 * 역할: 거래 확정 시 요청자의 코인을 임시 보관하는 엔티티입니다.
 * 서비스 완료 시 제공자에게 지급되거나, 취소 시 요청자에게 환불됩니다.
 */
@Entity
@Table(name = "pet_coin_escrow")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PetCoinEscrow extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "care_request_idx", nullable = false, unique = true)
    private CareRequest careRequest;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "care_application_idx")
    private CareApplication careApplication;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_idx", nullable = false)
    private Users requester; // 요청자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_idx", nullable = false)
    private Users provider; // 제공자

    @Column(nullable = false)
    private Integer amount; // 에스크로 금액 (코인 단위)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EscrowStatus status = EscrowStatus.HOLD;

    @Column(name = "released_at")
    private LocalDateTime releasedAt; // 지급 시간

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt; // 환불 시간
}
