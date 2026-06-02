package com.linkup.Petory.domain.notification.entity;

/** 알림 유형. CARE_REQUEST_COMMENT / BOARD_COMMENT / MISSING_PET_COMMENT. */
public enum NotificationType {
    CARE_REQUEST_COMMENT,    // 펫케어 요청글 댓글
    BOARD_COMMENT,           // 커뮤니티 게시글 댓글
    MISSING_PET_COMMENT      // 실종 제보 게시글 댓글
}

