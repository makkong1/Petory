package com.linkup.Petory.domain.user.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자 로그인 이벤트 기록. append-only — 삭제·수정 없이 쌓기만 한다. */
@Entity
@Table(name = "login_events", indexes = {
        @Index(name = "idx_login_events_user_login_at", columnList = "user_id, login_at"),
        @Index(name = "idx_login_events_login_at", columnList = "login_at")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "login_at", nullable = false)
    private LocalDateTime loginAt;

    /** LOCAL / GOOGLE / NAVER / KAKAO */
    @Column(name = "login_method", length = 16, nullable = false)
    private String loginMethod;
}
