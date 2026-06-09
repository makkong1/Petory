package com.linkup.Petory.domain.care.entity;

/**
 * 펫케어 요청의 일정 의미.
 * <ul>
 * <li>{@link #FIXED} — {@code date}를 약속(또는 희망) 시작 시각으로 본다.</li>
 * <li>{@link #FLEXIBLE_CHAT} — {@code date}는 참고만 하고, 채팅으로 최종 일정을 조율한다.</li>
 * </ul>
 */
public enum CareScheduleMode {
    FIXED,
    FLEXIBLE_CHAT
}
