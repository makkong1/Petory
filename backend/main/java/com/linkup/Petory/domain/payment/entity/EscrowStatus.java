package com.linkup.Petory.domain.payment.entity;

/**
 * 펫코인 에스크로 상태 열거형
 */
public enum EscrowStatus {
    HOLD,      // 보관중 (거래 확정 후 ~ 서비스 완료 전)
    RELEASED,  // 지급완료 (제공자에게 지급됨)
    REFUNDED   // 환불완료 (요청자에게 환불됨)
}
