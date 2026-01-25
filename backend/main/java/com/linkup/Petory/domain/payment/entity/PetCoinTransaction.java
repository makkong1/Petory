package com.linkup.Petory.domain.payment.entity;

import com.linkup.Petory.domain.common.BaseTimeEntity;
import com.linkup.Petory.domain.user.entity.Users;
import jakarta.persistence.*;
import lombok.*;

/**
 * 펫코인 거래 내역 엔티티
 * 역할: 펫코인의 모든 거래 내역을 기록하는 엔티티입니다.
 * 충전, 차감, 지급, 환불 등 모든 거래를 추적하여 감사(audit) 목적으로 사용됩니다.
 */
@Entity
@Table(name = "pet_coin_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PetCoinTransaction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(nullable = false)
    private Integer amount; // 거래 금액 (코인 단위)

    @Column(name = "balance_before", nullable = false)
    private Integer balanceBefore; // 거래 전 잔액

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter; // 거래 후 잔액

    @Column(name = "related_type", length = 50)
    private String relatedType; // 관련 엔티티 타입 (CARE_REQUEST 등)

    @Column(name = "related_idx")
    private Long relatedIdx; // 관련 엔티티 ID

    @Lob
    private String description; // 거래 설명

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.COMPLETED;
}
