package com.linkup.Petory.domain.user.entity;

/**
 * 이메일 인증 용도
 * 단일 이메일 인증 시스템에서 용도만 분리하여 사용
 */
public enum EmailVerificationPurpose {
    REGISTRATION, // 회원가입
    PASSWORD_RESET, // 비밀번호 변경
    PET_CARE, // 펫케어 서비스
    MEETUP, // 모임 서비스
    LOCATION_REVIEW, // 주변서비스 리뷰
    BOARD_EDIT, // 게시글 수정/삭제
    COMMENT_EDIT, // 댓글 수정/삭제
    MISSING_PET // 실종 제보 작성/수정/삭제
}
