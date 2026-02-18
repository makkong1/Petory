package com.linkup.Petory.domain.board.entity;

import com.linkup.Petory.domain.common.ContentStatus;
import com.linkup.Petory.domain.user.entity.Users;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

import com.linkup.Petory.domain.common.BaseTimeEntity;

@Entity
@Table(name = "board")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Board extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user;

    private String title;

    @Lob
    private String content;

    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ContentStatus status = ContentStatus.ACTIVE;

    @Builder.Default
    @Column(name = "view_count")
    private Integer viewCount = 0;

    @Builder.Default
    @Column(name = "like_count")
    private Integer likeCount = 0;

    /** [리팩토링] buildBoardSummary count 쿼리 제거 - dislikeCount 엔티티 필드로 실시간 업데이트 */
    @Builder.Default
    @Column(name = "dislike_count")
    private Integer dislikeCount = 0;


    @Builder.Default
    @Column(name = "comment_count")
    private Integer commentCount = 0;

    @Column(name = "last_reaction_at")
    private LocalDateTime lastReactionAt;

    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Comment> comments;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = ContentStatus.ACTIVE;
        }
    }
}
