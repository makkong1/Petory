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

    // 등록 시점에는 상대가 정해지지 않았으므로 NULL 로 시작하고, 거래 확정 때 배정된다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_idx")
    private Users provider; // 제공자 (NULL = 아직 상대 미정)

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

    /** 아직 상대가 정해지지 않은 보관인가. 확정 전이라는 뜻이고, 금액 수정이 가능한 구간이다. */
    public boolean isUnassigned() {
        return this.provider == null;
    }

    /**
     * 거래 확정 시 지급 대상을 배정한다. 이미 배정된 에스크로는 다시 배정하지 않는다
     * (같은 보관에 두 제공자가 붙으면 누구에게 줄지가 사라진다).
     */
    public void assignProvider(Users provider, CareApplication careApplication) {
        if (this.status != EscrowStatus.HOLD) {
            throw new IllegalStateException("보관 중(HOLD)인 에스크로만 배정할 수 있습니다. 현재: " + this.status);
        }
        if (this.provider != null) {
            throw PaymentConflictException.escrowAlreadyExists();
        }
        this.provider = provider;
        this.careApplication = careApplication;
    }

    /**
     * 보관 금액을 바꾼다. 상대가 정해지기 전(OPEN)에만 가능하다 —
     * 제공자가 그 금액을 보고 수락한 뒤에는 계약 조건이므로 바꿀 수 없다.
     *
     * 목표 금액을 받는다(증분이 아니라). 같은 요청이 두 번 도착해도 결과가 같아서
     * 별도 멱등 장치 없이 재시도 안전하다.
     */
    public void changeAmount(int newAmount) {
        if (this.status != EscrowStatus.HOLD) {
            throw new IllegalStateException("보관 중(HOLD)인 에스크로만 금액을 바꿀 수 있습니다. 현재: " + this.status);
        }
        if (this.provider != null) {
            throw new IllegalStateException("상대가 정해진 뒤에는 금액을 바꿀 수 없습니다.");
        }
        this.amount = newAmount;
    }

    public void release() {
        if (this.status != EscrowStatus.HOLD) {
            throw new IllegalStateException("HOLD 상태만 지급 가능");
        }
        if (this.provider == null) {
            throw new IllegalStateException("지급 대상이 배정되지 않은 에스크로는 지급할 수 없습니다.");
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
