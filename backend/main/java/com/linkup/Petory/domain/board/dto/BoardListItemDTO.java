package com.linkup.Petory.domain.board.dto;

import java.time.LocalDateTime;

import com.linkup.Petory.domain.common.ContentStatus;

import lombok.Getter;

/**
 * 게시글 목록 전용 읽기 모델(Read Model).
 *
 * <p>목록 화면(CommunityBoard)이 쓰는 게시글 본문 필드 + 작성자 3필드(idx/username/location)만 담는다.
 * 기존 목록 쿼리는 {@code JOIN FETCH b.user}로 작성자(Users) 27컬럼 전체를 로딩했으나,
 * JPQL 생성자 표현식 projection으로 작성자 3컬럼만 SELECT 하기 위한 목적의 DTO다.
 *
 * <p>리액션 카운트(좋아요/싫어요)와 첨부파일은 서비스 레이어가 배치로 사후 주입하므로 여기 담지 않는다.
 */
@Getter
public class BoardListItemDTO {

    private final Long idx;
    private final String title;
    private final String content;
    private final String category;
    private final ContentStatus status;
    private final LocalDateTime createdAt;
    private final Boolean isDeleted;
    private final LocalDateTime deletedAt;
    private final Integer commentCount;
    private final Integer likeCount;
    private final Integer dislikeCount;
    private final Integer viewCount;
    private final LocalDateTime lastReactionAt;
    // 작성자 — Users 엔티티 전체 대신 실제 사용하는 3컬럼만
    private final Long userId;
    private final String username;
    private final String userLocation;

    public BoardListItemDTO(Long idx, String title, String content, String category, ContentStatus status,
            LocalDateTime createdAt, Boolean isDeleted, LocalDateTime deletedAt, Integer commentCount,
            Integer likeCount, Integer dislikeCount, Integer viewCount, LocalDateTime lastReactionAt,
            Long userId, String username, String userLocation) {
        this.idx = idx;
        this.title = title;
        this.content = content;
        this.category = category;
        this.status = status;
        this.createdAt = createdAt;
        this.isDeleted = isDeleted;
        this.deletedAt = deletedAt;
        this.commentCount = commentCount;
        this.likeCount = likeCount;
        this.dislikeCount = dislikeCount;
        this.viewCount = viewCount;
        this.lastReactionAt = lastReactionAt;
        this.userId = userId;
        this.username = username;
        this.userLocation = userLocation;
    }
}
