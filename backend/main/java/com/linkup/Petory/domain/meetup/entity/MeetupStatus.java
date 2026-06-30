package com.linkup.Petory.domain.meetup.entity;

/**
 * 모임 상태. RECRUITING(모집 중) / CLOSED(마감) / COMPLETED(종료) / CANCELLED(주최자 제재로 취소).
 */
public enum MeetupStatus {
    RECRUITING, // 모집중
    CLOSED, // 마감
    COMPLETED, // 종료
    CANCELLED  // 주최자 제재로 취소 (제재 이벤트 후 설정)
}
