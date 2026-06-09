package com.linkup.Petory.domain.petRecommendation.entity;

import java.time.LocalDateTime;

import com.linkup.Petory.domain.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 반려생활 의도 signal 저장 엔티티.
 *
 * <p>
 * 원문 텍스트 대신 intent/도메인/추천 카테고리/신뢰도/만료시각 등 요약 신호만 보관한다.
 */
@Entity
@Table(name = "user_pet_intent_signal", indexes = {
    @Index(name = "idx_user_signal_active", columnList = "user_idx, expires_at, created_at"),
    @Index(name = "idx_signal_source", columnList = "source_type, source_id")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPetIntentSignal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_idx", nullable = false)
    private Long userIdx;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;   // COMMUNITY | CARE | LOCATION_SEARCH (signal 발생 출처)

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "intent_domain", nullable = false, length = 50)
    private String intentDomain;

    @Column(name = "intent", nullable = false, length = 50)
    private String intent;

    @Column(name = "recommended_categories", columnDefinition = "JSON")
    private String recommendedCategories;

    @Column(name = "confidence", nullable = false)
    private Double confidence;

    @Column(name = "urgency", length = 10)
    private String urgency;  // "HIGH" | "NORMAL" | "LOW" | null (레거시 행 호환)

    @Column(name = "intent_tags", columnDefinition = "JSON")
    private String intentTags;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
