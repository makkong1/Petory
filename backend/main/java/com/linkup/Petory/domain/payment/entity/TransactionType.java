package com.linkup.Petory.domain.payment.entity;

/**
 * 펫코인 거래 타입 열거형
 */
public enum TransactionType {
    CHARGE,   // 충전
    DEDUCT,   // 차감 (에스크로로 이동)
    PAYOUT,   // 지급 (에스크로에서 제공자에게)
    REFUND    // 환불
}
