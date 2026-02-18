package com.linkup.Petory.domain.payment.dto;

import java.time.LocalDateTime;

import com.linkup.Petory.domain.payment.entity.TransactionStatus;
import com.linkup.Petory.domain.payment.entity.TransactionType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 거래 상세 DTO (상대방 정보 포함)
 * - 누구랑 얼마에 거래했는지 표시용
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetCoinTransactionDetailDTO {
    private Long idx;
    private Long userId;
    private TransactionType transactionType;
    private Integer amount;
    private Integer balanceBefore;
    private Integer balanceAfter;
    private String relatedType;
    private Long relatedIdx;
    private String description;
    private TransactionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 거래 상대방 사용자 ID (CARE_REQUEST 등 관련 거래 시) */
    private Long counterpartyUserId;
    /** 거래 상대방 닉네임/사용자명 */
    private String counterpartyUsername;
    /** 관련 거래 제목 (예: 펫케어 요청 제목) */
    private String relatedTitle;
}
