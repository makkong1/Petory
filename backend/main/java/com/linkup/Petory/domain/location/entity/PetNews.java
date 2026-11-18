package com.linkup.Petory.domain.location.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수집한 반려동물 소식 1건. 현재 출처는 서울시 보도자료뿐이다.
 *
 * <p>
 * 게시판이 아니다 — 전용 조회 화면이 없고 사용자는 알림·지도 배너로만 본다.
 * <b>본문은 저장하지 않는다.</b> 원문에 공공누리 표시가 확인되지 않아(2026-11-18 조사)
 * 제목·부서·등록일 같은 사실 정보만 두고 본문은 {@link #sourceUrl} 로 보낸다.
 */
@Entity
@Table(name = "pet_news")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PetNews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Column(name = "source", nullable = false, length = 30)
    private String source;

    /** 원문 게시물 번호(upperNo). {@code source} 와 묶여 UNIQUE 이고, 이것이 멱등의 근거다. */
    @Column(name = "source_no", nullable = false, length = 20)
    private String sourceNo;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "dept", length = 100)
    private String dept;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private NewsCategory category;

    /** 원문 등록일. 마감일은 목록에 없어 수집하지 않는다(V25 주석 참고). */
    @Column(name = "published_at", nullable = false)
    private LocalDate publishedAt;

    @Column(name = "source_url", nullable = false, length = 500)
    private String sourceUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 제목 키워드 룰로 매긴다. 어디에도 안 걸리면 적재하지 않는다(보도참고자료 등 잡음 제거). */
    public enum NewsCategory {
        EVENT, EDUCATION, SAFETY, SUPPORT
    }

    @Builder
    private PetNews(String source, String sourceNo, String title, String dept,
            NewsCategory category, LocalDate publishedAt, String sourceUrl, LocalDateTime createdAt) {
        this.source = source;
        this.sourceNo = sourceNo;
        this.title = title;
        this.dept = dept;
        this.category = category;
        this.publishedAt = publishedAt;
        this.sourceUrl = sourceUrl;
        this.createdAt = createdAt;
    }
}
