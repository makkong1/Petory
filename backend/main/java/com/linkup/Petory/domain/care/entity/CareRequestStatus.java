package com.linkup.Petory.domain.care.entity;

/**
 * 펫케어 요청 상태. OPEN(모집 중) / IN_PROGRESS(진행 중) / COMPLETED(완료) / CANCELLED(취소).
 */
public enum CareRequestStatus {
    OPEN, IN_PROGRESS, COMPLETED, CANCELLED;

    /**
     * 허용된 다음 상태인지. COMPLETED / CANCELLED 는 종착이라 어디로도 못 간다.
     *
     * 가드가 없을 때 COMPLETED -> CANCELLED 가 통했고, 그 경우 에스크로는 이미 RELEASED 라
     * 환불이 스킵돼 "상태는 취소인데 돈은 제공자에게 가 있는" 상태가 만들어졌다.
     */
    public boolean canTransitionTo(CareRequestStatus next) {
        return switch (this) {
            case OPEN -> next == IN_PROGRESS || next == CANCELLED;
            case IN_PROGRESS -> next == COMPLETED || next == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
