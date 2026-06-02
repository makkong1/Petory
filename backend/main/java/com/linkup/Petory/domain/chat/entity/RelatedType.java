package com.linkup.Petory.domain.chat.entity;

/** 대화방이 연결된 도메인 유형. 어떤 도메인 엔티티와 연관된 채팅방인지를 구분한다. */
public enum RelatedType {
    CARE_REQUEST,
    CARE_APPLICATION,
    MISSING_PET_BOARD,
    MEETUP,
    USER
}

