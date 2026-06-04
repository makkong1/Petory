package com.linkup.Petory.domain.petRecommendation.entity;

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
 * 사용자-장소 상호작용 로그 엔티티.
 *
 * <p>
 * 추천 시점의 인기 점수 집계를 위해 이벤트성 데이터를 적재한다.
 */
@Entity
@Table(name = "place_interaction_log", indexes = {
    @Index(name = "idx_place_interaction", columnList = "location_idx, created_at")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceInteractionLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_idx")
    private Long userIdx;

    @Column(name = "location_idx", nullable = false)
    private Long locationIdx;

    @Column(name = "interaction_type", nullable = false, length = 20)
    private String interactionType;   // VIEW | NAVIGATE | FAVORITE (행동 신호)

}
