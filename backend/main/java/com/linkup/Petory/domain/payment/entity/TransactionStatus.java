package com.linkup.Petory.domain.payment.entity;

/**
 * 펫코인 거래 상태 열거형
 */
public enum TransactionStatus {
    PENDING,    // 대기중
    COMPLETED,  // 완료
    FAILED,     // 실패
    CANCELLED   // 취소
}
