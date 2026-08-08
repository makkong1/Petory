package com.linkup.Petory.domain.chat.entity;

import java.time.LocalDateTime;

import com.linkup.Petory.domain.common.BaseTimeEntity;
import com.linkup.Petory.domain.user.entity.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 대화방 참여자 엔티티. 참여자의 역할·읽음 상태·거래 확인 여부를 관리한다.
 */
@Entity
@Table(name = "conversationparticipant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationParticipant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_idx", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @Builder.Default
    private ParticipantRole role = ParticipantRole.MEMBER;

    @Column(name = "unread_count", nullable = false)
    @Builder.Default
    private Integer unreadCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_idx")
    private ChatMessage lastReadMessage;

    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ParticipantStatus status = ParticipantStatus.ACTIVE;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "deal_confirmed")
    @Builder.Default
    private Boolean dealConfirmed = false;

    @Column(name = "deal_confirmed_at")
    private LocalDateTime dealConfirmedAt;

    /**
     * 확정 당시 동의한 제시 금액. 현재 금액과 다르면 낡은 동의다.
     * 시각 비교(확정 시각 vs 금액 변경 시각)는 datetime 초 정밀도에서 같은 초를 구분하지 못해 새는데,
     * 금액을 직접 들고 있으면 시계와 무관하게 판정된다.
     */
    @Column(name = "confirmed_offered_coins")
    private Integer confirmedOfferedCoins;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        this.joinedAt = LocalDateTime.now();
        if (this.role == null) {
            this.role = ParticipantRole.MEMBER;
        }
        if (this.unreadCount == null) {
            this.unreadCount = 0;
        }
        if (this.status == null) {
            this.status = ParticipantStatus.ACTIVE;
        }
        if (this.isDeleted == null) {
            this.isDeleted = false;
        }
    }

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    // 읽지 않은 메시지 수 증가
    public void incrementUnreadCount() {
        if (this.unreadCount == null) {
            this.unreadCount = 0;
        }
        this.unreadCount++;
    }

    // 읽지 않은 메시지 수 감소 (0 이하로 내려가지 않음)
    public void decrementUnreadCount() {
        if (this.unreadCount != null && this.unreadCount > 0) {
            this.unreadCount--;
        }
    }
}
