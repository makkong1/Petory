package com.linkup.Petory.domain.board.entity;

import com.linkup.Petory.domain.common.BaseTimeEntity;
import com.linkup.Petory.domain.user.entity.Users;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 댓글 리액션(좋아요/싫어요) 엔티티. 사용자 1명이 댓글 1개에 1개의 리액션만 가질 수 있다. */
@Entity
@Table(name = "comment_reaction", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "comment_idx", "user_idx" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentReaction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne
    @JoinColumn(name = "comment_idx", nullable = false)
    private Comment comment;

    @ManyToOne
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false)
    private ReactionType reactionType;

    public void changeReactionType(ReactionType newType) {
        this.reactionType = newType;
    }
}
