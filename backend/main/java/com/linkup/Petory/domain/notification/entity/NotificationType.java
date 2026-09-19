package com.linkup.Petory.domain.notification.entity;

/**
 * 알림 유형. DB 컬럼이 varchar 라 값 추가에 마이그레이션이 필요 없다.
 */
public enum NotificationType {
    CARE_REQUEST_COMMENT, // 펫케어 요청글 댓글
    BOARD_COMMENT, // 커뮤니티 게시글 댓글
    MISSING_PET_COMMENT, // 실종 제보 게시글 댓글
    PET_HEALTH_ALERT,        // MEDICAL+HIGH urgency signal 저장 시 발송

    // 케어 계약(제안/수락). 제안은 상대가 보고 있지 않을 때 도착하므로 알림이 없으면
    // 채팅방을 다시 열어볼 이유가 생기지 않는다.
    CARE_OFFER_RECEIVED, // 요청자 -> 제공자: 케어를 맡아달라는 제안이 도착
    CARE_OFFER_ACCEPTED, // 제공자 -> 요청자: 제안을 수락, 계약 성립
    CARE_OFFER_REJECTED, // 제공자 -> 요청자: 제안을 거절
    CARE_OFFER_WITHDRAWN // 요청자 사정으로 제안이 내려감 (금액 변경·요청 취소·삭제)
}
