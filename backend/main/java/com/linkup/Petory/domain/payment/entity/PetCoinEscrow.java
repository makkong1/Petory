package com.linkup.Petory.domain.payment.entity;

import java.time.LocalDateTime;

import com.linkup.Petory.domain.care.entity.CareApplication;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.common.BaseTimeEntity;
import com.linkup.Petory.domain.payment.exception.PaymentConflictException;
import com.linkup.Petory.domain.user.entity.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 펫코인 에스크로 엔티티 역할: 거래 확정 시 요청자의 코인을 임시 보관하는 엔티티입니다. 서비스 완료 시 제공자에게 지급되거나, 취소 시
 * 요청자에게 환불됩니다.
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

    public void release() {
        if (this.status != EscrowStatus.HOLD) {
            throw new IllegalStateException("HOLD 상태만 지급 가능");
        }
        this.status = EscrowStatus.RELEASED;
        this.releasedAt = LocalDateTime.now();
    }

    public void refund() {
        if (this.status != EscrowStatus.HOLD) {
            throw PaymentConflictException.holdStatusRequiredForRefund();
        }
        this.status = EscrowStatus.REFUNDED;
        this.refundedAt = LocalDateTime.now();
    }
}
