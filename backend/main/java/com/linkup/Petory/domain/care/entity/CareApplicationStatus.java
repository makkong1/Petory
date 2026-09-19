package com.linkup.Petory.domain.care.entity;

/**
 * 케어 제안 상태.
 *
 * <ul>
 *   <li>{@code PENDING} — 요청자가 보냈고 제공자가 아직 답하지 않았다</li>
 *   <li>{@code ACCEPTED} — 제공자가 수락했다. 계약 성립</li>
 *   <li>{@code REJECTED} — <b>제공자가</b> 거절했다</li>
 *   <li>{@code WITHDRAWN} — <b>요청자 사정으로</b> 내려갔다(금액 변경·요청 취소·요청 삭제)</li>
 *   <li>{@code EXPIRED} — <b>아무도 답하지 않은 채</b> 기간이 지났다</li>
 * </ul>
 *
 * 마지막 둘을 굳이 나눈 이유: 한 값으로 뭉치면 제공자 화면에 "거절하셨습니다"로 남는다.
 * 제공자가 아무것도 안 했는데 그렇게 적히면 상태가 거짓말을 하는 것이다.
 */
public enum CareApplicationStatus {
    PENDING, ACCEPTED, REJECTED, WITHDRAWN, EXPIRED
}
