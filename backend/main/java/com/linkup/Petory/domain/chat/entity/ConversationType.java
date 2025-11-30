package com.linkup.Petory.domain.chat.entity;

public enum ConversationType {
    DIRECT,           // 1:1 일반 채팅
    GROUP,            // 그룹 채팅
    CARE_REQUEST,     // 펫케어 요청 채팅
    MISSING_PET,      // 실종제보 채팅
    MEETUP,           // 산책모임 채팅
    ADMIN_SUPPORT     // 관리자 지원 채팅
}

