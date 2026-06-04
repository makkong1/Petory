package com.linkup.Petory.domain.petRecommendation.repository;

/**
 * PlaceInteractionLog 집계 결과 프로젝션 (Object[] 대체).
 *
 * Repository 쿼리 결과를 받는 Projection DTO(Projection DTO)
 *
 * @param locationIdx 위치 ID
 * @param count 위치 집계 결과 카운트
 */
public record LocationInteractionCount(Long locationIdx, Long count) {

}
