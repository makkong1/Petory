package com.linkup.Petory.domain.care.dto;

import java.time.LocalDateTime;

/**
 * 케어 요청 목록/지도 전용 읽기 모델(Read Model) — native 쿼리 인터페이스 projection.
 *
 * <p>지도(getNearby)는 기존에 {@code SELECT cr.*}로 엔티티를 읽은 뒤 컨버터가
 * 작성자(Users) 전체·중첩 {@code PetDTO}(파일 경로 조회)·{@code applications}(@BatchSize 추가 쿼리)까지
 * 채웠으나, 지도 레이어(CareLayer/UnifiedMap)가 실제로 쓰는 필드는 아래 14개뿐이다.
 * 필요한 컬럼만 JOIN·SELECT 하는 projection으로 전환해 연관 오버페칭(추가 쿼리)과
 * 컬럼 오버페칭을 동시에 제거한다.
 *
 * <p>{@code petName}은 프론트가 읽는 평면 필드({@code raw.petName})와 일치시킨 것이다
 * (기존 중첩 {@code pet.name}과의 불일치도 해소).
 */
public interface CareRequestListView {
    Long getIdx();
    String getTitle();
    String getDescription();
    LocalDateTime getDate();
    String getScheduleMode();
    Integer getEstimatedDurationMinutes();
    Integer getOfferedCoins();
    String getStatus();
    Double getLatitude();
    Double getLongitude();
    String getAddress();
    Long getUserId();
    String getUsername();
    String getPetName();
}
