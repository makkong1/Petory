package com.linkup.Petory.domain.payment.converter;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.payment.dto.PetCoinTransactionDTO;
import com.linkup.Petory.domain.payment.entity.PetCoinTransaction;

import lombok.RequiredArgsConstructor;

/**
 * PetCoinTransaction 엔티티와 DTO 간 변환을 담당하는 Converter입니다.
 * [리팩토링] transaction.getUser() 접근 시 N+1 → Repository @EntityGraph로 user 미리 로드
 */
@Component
@RequiredArgsConstructor
public class PetCoinTransactionConverter {

    public PetCoinTransactionDTO toDTO(PetCoinTransaction transaction) {
        if (transaction == null) {
            return null;
        }

        return PetCoinTransactionDTO.builder()
                .idx(transaction.getIdx())
                .userId(transaction.getUser() != null ? transaction.getUser().getIdx() : null)
                .transactionType(transaction.getTransactionType())
                .amount(transaction.getAmount())
                .balanceBefore(transaction.getBalanceBefore())
                .balanceAfter(transaction.getBalanceAfter())
                .relatedType(transaction.getRelatedType())
                .relatedIdx(transaction.getRelatedIdx())
                .description(transaction.getDescription())
                .status(transaction.getStatus())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }
}
