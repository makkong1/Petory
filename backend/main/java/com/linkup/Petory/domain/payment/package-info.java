/**
 * 펫코인·에스크로 결제 도메인.
 *
 * <p><b>care ↔ payment 순환은 의도된 결합(결제-거래 애그리거트)이다.</b>
 * {@code PetCoinEscrow}가 {@code CareRequest}를 {@code @OneToOne} FK로 물고 있어(데이터 모델 결합)
 * 이 방향은 이벤트로 풀 수 없고, 반대 방향은 {@code CareRequestService}가 거래 완료/취소 시
 * {@code releaseToProvider}/{@code refundToRequester}를 동기 호출하는 money-critical 오케스트레이션이라
 * 비동기화하면 "거래는 완료됐는데 코인은 안 나감" 정합성 사고가 난다. 따라서 care·payment는
 * 함께 배포·변경되는 하나의 애그리거트로 보고 순환을 수용한다.
 * 상세: docs/analysis/domain-dependency-refactoring-2026-07.md §4.1
 */
package com.linkup.Petory.domain.payment;
