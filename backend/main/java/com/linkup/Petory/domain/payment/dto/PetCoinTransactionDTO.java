package com.linkup.Petory.domain.payment.dto;

import java.time.LocalDateTime;

import com.linkup.Petory.domain.payment.entity.TransactionStatus;
import com.linkup.Petory.domain.payment.entity.TransactionType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 코인 거래 내역 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetCoinTransactionDTO {
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
}
