package com.linkup.Petory.domain.petRecommendation.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "signal_interaction_log", indexes = {
    @Index(name = "idx_signal_log_user",   columnList = "user_idx, created_at"),
    @Index(name = "idx_signal_log_signal", columnList = "signal_id"),
    @Index(name = "idx_signal_log_domain", columnList = "intent_domain, interaction_type")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalInteractionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_idx", nullable = false)
    private Long userIdx;

    @Column(name = "signal_id", nullable = false)
    private Long signalId;

    @Column(name = "intent_domain", nullable = false, length = 50)
    private String intentDomain;

    @Column(name = "target_tab", length = 30)
    private String targetTab;

    @Column(name = "target_category", length = 100)
    private String targetCategory;

    @Column(name = "interaction_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private InteractionType interactionType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public enum InteractionType {
        CLICKED,    // 추천 카드 클릭 (카테고리 검색/액션 진입)
        DISMISSED,  // 추천 카드 닫기/숨기기
        CONVERTED   // 작성/검색 완료까지 추적 (Phase 2 이후 추가)
    }
}
