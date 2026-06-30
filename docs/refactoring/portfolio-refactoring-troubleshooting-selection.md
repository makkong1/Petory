# 리팩토링·트러블슈팅 포트폴리오 선별 기준

> 목적: `docs/refactoring/`, `docs/troubleshooting/`에 흩어진 작업 기록 중 포트폴리오 본문에 넣을 사례와 부록으로만 둘 사례를 구분한다.  
> 기준: 면접에서 포트폴리오를 보지 않고도 문제, 원인, 해결, 검증을 말할 수 있는 사례만 본문에 둔다.

---

## 결론

리팩토링·트러블슈팅 문서를 전부 본문에 넣지 않는다.

관련 문서는 100개 이상이며, 대부분은 세부 분석, 백로그, 반복 리팩토링, 도메인별 보조 기록이다. 본문에 많이 넣으면 성과가 커 보이기보다 면접에서 방어해야 할 범위만 넓어진다.

포트폴리오 본문에는 아래 4개만 대표 사례로 둔다.

| 우선순위 | 본문 사례 | 이유 |
| --- | --- | --- |
| 1 | N+1 성능 개선 | 정량 수치가 가장 강하고 여러 도메인에 반복 적용된 패턴이다. |
| 2 | 동시성/Race Condition 해결 | 락, 원자적 쿼리, 트랜잭션 격리 수준을 설명하기 좋다. |
| 3 | Location 초기 로드 최적화 | 사용자 체감 성능, DB 필터링, 네트워크 전송량 감소를 말할 수 있다. |
| 4 | 보안/인가 계약 정리 | 클라이언트 userId 신뢰 제거, JWT 인증 주체 사용, 리소스 참여자 검증을 보여준다. |

선택 사례로는 petRecommendation의 NLP 계약 버그를 둔다. 추천/NLP 파트를 강조하는 포트폴리오라면 본문에 넣고, 백엔드 성능·안정성 중심이면 부록으로 뺀다.

---

## 선별 기준

본문에 넣는 조건:

- 수치가 있다: 쿼리 수, 실행 시간, 메모리, 스캔 행 수, 실패 재현 테스트.
- 원인이 기술적으로 설명된다: JPA lazy loading, read-modify-write, 트랜잭션 격리, 인덱스 미사용, API 계약 불일치.
- 해결 방식에 선택 이유가 있다: Fetch Join vs batch 조회, 비관적 락 vs 원자적 UPDATE, Java 필터링 vs DB WHERE.
- 면접에서 1분 안에 말할 수 있다.
- 실제 사용자 영향이 있다: 느린 응답, 데이터 불일치, 권한 우회, silent drop.

본문에서 제외하는 조건:

- 단순 반복 작업이다: DTO record 전환, 예외 클래스 정리, 도메인별 Fetch 전략 목록.
- 아직 백로그이거나 잠재 이슈다.
- 정량 근거가 약하다.
- 본문 대표 사례와 같은 문제를 다른 도메인에서 반복한 사례다.
- 파일명·클래스명을 알아야만 설명 가능한 세부 작업이다.

---

## 본문 사례 1: N+1 성능 개선

### 포트폴리오 문구

게시판, 케어, 채팅, 실종 제보 목록 조회에서 발생한 N+1 문제를 배치 조회, Fetch Join, Map 기반 매핑으로 개선했다. 게시판 목록 조회는 100개 게시글 기준 쿼리 수를 301개에서 3개로 줄였다. 케어 요청 목록은 기존 비페이징 측정에서 약 2,400개 쿼리를 4~5개 수준으로 줄였고, 현재 페이징 경로도 `@BatchSize`와 배치 변환으로 N+1을 완화한다. 같은 패턴을 채팅방 목록과 실종 제보 목록에도 적용해 조회 대상 수가 늘어도 쿼리 수가 일정하게 유지되도록 만들었다.

### 면접 답변 골격

문제:
목록 API에서 부모 엔티티를 먼저 조회한 뒤 작성자, 반응, 파일, 댓글 수, 지원자 수, 예방접종 같은 연관 데이터를 항목마다 개별 조회해 쿼리 수가 선형 증가했다.

원인:
JPA LAZY 로딩과 Converter 내부 연관 접근, 루프 안 Repository 단건 호출이 섞여 있었다.

해결:
목록에 필요한 ID를 먼저 모은 뒤 `IN` 절 배치 조회로 한 번에 가져오고, `Map<Long, ...>`으로 변환해 DTO 조립 시 메모리에서 매핑했다. 작성자처럼 항상 필요한 `ManyToOne`은 Fetch Join을 사용했다.

검증:
Hibernate Statistics와 성능 로그로 쿼리 수, 실행 시간, 메모리 사용량을 비교했다.

### 근거 문서

- [Board 성능 트러블슈팅](../troubleshooting/board/performance-optimization.md)
- [Care 요청 N+1 분석](../troubleshooting/care/care-request-n-plus-one-analysis.md)
- [로그인 시 N+1 문제 해결](../troubleshooting/users/login-n-plus-one-issue.md)
- [채팅방 참여자 조회 N+1](../troubleshooting/chat/n-plus-one-conversationparticipant.md)
- [MissingPet N+1 문제 해결](../troubleshooting/missing-pet/n-plus-one-query-issue.md)

### 대표 수치

| 도메인 | Before | After | 효과 |
| --- | --- | --- | --- |
| Board | 301 queries, 745ms, 22.50MB | 3 queries, 30ms, 2MB | 쿼리 99% 감소 |
| Care 기존 비페이징 측정 | 약 2,400 queries, 1,084ms, 21MB | 4~5 queries, 66ms, 6MB | 쿼리 99.8%, 시간 94% 감소 |
| Chat login | 21 queries, 305ms, 0.58MB | 4 queries, 55ms, 0.13MB | 실행 시간 81.97% 단축 |
| MissingPet | 105 queries, 571ms, 11MB | 3 queries, 106ms, 3MB | 쿼리 97% 감소 |

---

## 본문 사례 2: 동시성/Race Condition 해결

### 포트폴리오 문구

모임 참가와 펫코인 잔액 변경에서 발생 가능한 Race Condition을 재현하고 도메인 특성에 맞게 해결했다. 모임 참가 인원 카운트는 조건부 원자적 UPDATE로 처리해 최대 인원 초과를 막았고, 펫코인 잔액 변경은 `SELECT ... FOR UPDATE` 기반 비관적 락으로 read-modify-write 손실을 막았다. 케어 거래 확정의 stuck state는 같은 동시성 계열의 보조 사례로 설명한다.

### 면접 답변 골격

문제:
동시에 여러 요청이 같은 행을 읽고 수정하면 같은 이전 값을 기준으로 계산해 Lost Update가 발생하거나, 둘 다 조건 검사를 통과해 비즈니스 제약이 깨질 수 있었다.

원인:
`현재 값 조회 -> 조건 확인 -> 값 변경 -> save` 구조가 원자적이지 않았다. 모임 참가에서는 최대 인원 체크와 증가가 분리되어 있었고, 펫코인 잔액 변경에서는 여러 트랜잭션이 같은 잔액을 읽고 덮어쓸 수 있었다.

해결:
모임 참가처럼 단순 카운터와 조건 검사가 함께 필요한 영역은 DB의 조건부 UPDATE 한 문장으로 처리했다. 잔액처럼 현재 값 검증이 필요한 영역은 비관적 락으로 직렬화했다. 케어 거래 확정은 두 참여자의 상태를 함께 판단해야 해서 Conversation 행을 기준으로 락을 잡은 보조 사례로만 짧게 설명한다.

검증:
동시성 테스트로 예상 최종 상태와 실제 DB 상태를 비교했다.

### 근거 문서

- [Meetup 참가 Race Condition](../troubleshooting/meetup/race-condition-participants.md)
- [Care 거래 확정 Race Condition](../troubleshooting/care/care-deal-confirmation-race-condition.md)
- [PetCoinService Race Condition](./payment/petcoin-service-race-condition.md)
- [동시성 개념 정리](../interview/concepts/03_동시성_제어.md)

### 대표 수치·상황

| 사례 | 문제 | 해결 |
| --- | --- | --- |
| Meetup 참가 | 최대 3명 제한인데 동시 요청으로 4명 참가 가능 | `currentParticipants < maxParticipants` 조건부 원자적 UPDATE |
| PetCoin | 동시 충전 5건 기대 잔액 150, 실제 110 가능 | `findByIdForUpdate`로 순차 반영 |
| 보조: Care 거래 확정 | 두 사용자 모두 확정했는데 `CareRequest`가 `OPEN`에 머무는 stuck state | Conversation `PESSIMISTIC_WRITE` |

---

## 본문 사례 3: Location 초기 로드 최적화

### 포트폴리오 문구

위치 서비스 초기 로드에서 약 22,699개 전체 데이터를 전송하고 프론트엔드에서 필터링하던 구조를 제거했다. 현재 주변서비스의 기본 조회, "내 위치", "이 지역" 검색은 사용자 위치 또는 지도 중심 기준 좌표+반경 검색을 사용한다. 이 변경으로 초기 조회 데이터를 약 1,026개로 줄이고, 네트워크 전송량과 프론트 처리 시간을 낮췄다. 지역명 검색은 이후 여러 번 검색 UX가 바뀐 영역이라 본문 대표 성과가 아니라 참고 기록으로만 둔다.

### 면접 답변 골격

문제:
사용자가 실제로 보는 데이터는 일부인데 초기 로드에서 전체 위치 서비스 데이터를 가져와 DB, 네트워크, 브라우저 메모리에 모두 부담이 있었다.

원인:
초기 로드는 검색 조건을 DB WHERE로 충분히 밀어 넣지 못했고, 프론트엔드에서 지역 필터링과 거리 계산을 수행했다. 즉 사용자가 실제로 보는 지도 범위보다 훨씬 큰 데이터를 먼저 받은 뒤 브라우저에서 줄이는 구조였다.

해결:
사용자 위치 또는 지도 중심 기준 반경 조회를 백엔드에서 수행해 전체 데이터 전송을 줄였다. 현재 코드 기준으로 주변서비스 기본 조회와 "이 지역" 버튼은 좌표+반경 검색을 타며, 검색창의 지역명 처리 방식은 지오코딩과 지역 파라미터 분기가 섞인 보조 흐름으로 본문 성과에서 제외한다.

검증:
조회 데이터 수, DB 쿼리 시간, 네트워크 전송량, 프론트 메모리 사용량을 비교했다.

### 근거 문서

- [Location 초기 로드 성능 문제](../troubleshooting/location/initial-load-performance.md)
- [Location 검색 전략 비교](../troubleshooting/location/search-strategy-comparison.md) — 이후 검색 UX 변경 이력이 섞여 있어 본문 근거가 아니라 참고 문서로만 사용
- [Location 지도 결과 안정성 리팩토링](./location/지도-결과-안정성-리팩토링.md)

### 대표 수치

전체 조회 제거

| 항목 | Before | After |
| --- | --- | --- |
| 초기 조회 데이터 | 22,699개 | 약 1,026개 |
| 네트워크 전송량 | 약 22MB | 약 1MB |
| 프론트 전체 처리 | 1,484ms | 약 700ms |
| 프론트 메모리 | 78.90MB | 약 28.6MB |

---

## 본문 사례 4: 보안/인가 계약 정리

### 포트폴리오 문구

Chat API에서 클라이언트가 `userId`, `senderIdx` 같은 식별자를 직접 전달하거나, 채팅방 참여자 검증 없이 메시지 조회·검색·상태 변경을 수행할 수 있던 계약을 JWT 인증 주체 기반으로 정리했다. `getMessagesBefore`, `searchMessages`, `PATCH /status` 같은 경로에서 `requireActiveParticipant` 패턴으로 ACTIVE 참여자 여부를 확인하게 했고, Care의 `my-requests`는 보조 사례로 `userId` 쿼리 파라미터를 제거했다. 핵심은 "클라이언트가 넘긴 사용자 식별자를 신뢰하지 않고, 서버가 인증 주체와 리소스 관계를 기준으로 판단한다"는 계약으로 바꾼 것이다.

### 면접 답변 골격

문제:
인증된 사용자가 요청 파라미터의 사용자 ID를 바꾸거나, 참여자가 아닌 채팅방 ID를 지정하면 메시지 조회·검색·상태 변경 대상에 영향을 줄 수 있는 API 계약이 있었다.

원인:
인증은 되어 있지만, 리소스 소유자나 참여자인지 확인하는 인가가 API별로 흩어져 있었다. 특히 Chat의 메시지 조회·검색·상태 변경 경로는 인증 여부와 채팅방 참여 여부가 분리되어 있었고, 일부 API는 서버의 인증 주체보다 클라이언트가 넘긴 ID를 조회 기준으로 삼았다.

해결:
Chat에서는 사용자 식별을 JWT principal에서 가져오고, 서비스 진입 전에 `requireActiveParticipant`로 채팅방 ACTIVE 참여자 여부를 검증했다. Care `my-requests`는 클라이언트의 `userId` 파라미터를 계약에서 제거해 현재 사용자 기준 조회로 바꿨다. 메서드 단 `@PreAuthorize`도 명시해 인가 의도를 코드에서 확인 가능하게 했다.

검증:
Chat은 메시지 조회·검색·상태 변경 경로에서 활성 참여자 검증을 거치도록 점검했다. Care `my-requests`는 `userId` 쿼리 파라미터 없이 현재 사용자 기준으로 조회되도록 계약을 바꿨다. 단, 이 사례는 N+1이나 동시성처럼 수치 중심 성과가 아니라 API 계약과 인가 경계 개선 사례로 설명한다.

### 근거 문서

- [Care & Payment 리팩토링 기록](./care/care-payment-refactoring-2026-04-14.md)
- [Chat 보안·트랜잭션 정리](./chat/chat-backend-security-transaction-2026-04-14.md)
- [인증 주체 조회 리팩터링 점검](./authentication-principal-refactoring.md)
- [보안 & JWT 개념 정리](../interview/concepts/09_보안_JWT_인증.md)

---

## 선택 사례: petRecommendation NLP 계약 버그

추천/NLP를 포트폴리오에서 강조한다면 본문에 넣는다. 그렇지 않으면 부록으로 둔다.

### 포트폴리오 문구

Java와 Python FastAPI 서버 사이의 `petType` enum 계약이 달라 특정 반려동물 종의 의도 분석 신호가 조용히 저장되지 않는 문제를 해결했다. Java는 `BIRD`, `RABBIT`, `HAMSTER`, `ETC`를 보냈지만 Python 스키마는 `DOG`, `CAT`, `OTHER`만 허용해 422가 발생했고, 클라이언트는 예외를 삼켜 signal이 무음 드롭됐다. Java-Python 경계에서 `DOG`, `CAT` 외 값을 `OTHER`로 정규화해 변경 범위를 최소화했다.

### 근거 문서

- [petType 422 silent drop](../troubleshooting/petRecommendation/pettype-422-silent-drop-2026-06-10.md)
- [NLP 서버 품질 & 계약 이슈](../troubleshooting/petRecommendation/nlp-server-issues-2026-06-09.md)
- [NLP 서버 개념 정리](../interview/concepts/13_NLP_서버_FastAPI.md)

---

## 부록으로 둘 문서

아래는 작업 기록으로는 의미가 있지만, 포트폴리오 본문에는 넣지 않는다.

| 분류 | 처리 |
| --- | --- |
| DTO record 전환 | 부록. 반복 리팩토링이고 성능·장애 임팩트가 약하다. |
| 예외처리 리팩토링 | 부록. 도메인별 반복 문서가 많아 본문을 흐린다. |
| Fetch Join vs Batch Size 도메인별 정리 | N+1 본문 사례의 보조 근거로만 링크한다. |
| potential issues / backlog | 본문 제외. 실제 적용·검증된 사례와 섞지 않는다. |
| 코드 리뷰 원문 | 부록. 본문에는 적용 결과만 쓴다. |
| 인덱스 전략 일반론 | 단독 본문보다 Board/Location 사례 안의 해결 근거로 포함한다. |
| 인기글 스냅샷/FULLTEXT | Board 성능 개선 안의 세부 항목으로 짧게 언급한다. |

---

## 최종 포트폴리오 구성 예시

```md
## 성능 및 안정성 개선

### 1. JPA N+1 개선
- 문제: 목록 조회에서 연관 데이터를 항목마다 개별 조회
- 해결: IN 절 배치 조회, Fetch Join, Map 기반 DTO 조립
- 결과: Board 301 queries -> 3 queries, Care 기존 비페이징 측정 약 2,400 queries -> 4~5 queries

### 2. 동시성 제어
- 문제: 모임 참가와 펫코인 잔액 변경에서 Race Condition 가능
- 해결: 조건부 원자적 UPDATE, SELECT FOR UPDATE
- 결과: 인원 초과와 Lost Update 방지. 케어 확정 stuck state는 보조 사례로 설명

### 3. Location 초기 로드 최적화
- 문제: 초기 로드 시 전체 위치 데이터 조회 및 프론트 필터링
- 해결: 기본 조회를 사용자 위치/지도 중심 기반 반경 조회로 제한
- 결과: 초기 조회 데이터 22,699개 -> 약 1,026개, 네트워크 전송량 약 22MB -> 약 1MB

### 4. 보안/인가 계약 정리
- 문제: Chat 메시지 조회·검색·상태 변경에서 참여자 검증이 부족한 API 계약
- 해결: JWT principal 기반 사용자 식별, requireActiveParticipant 검증, userId 파라미터 제거
- 결과: 클라이언트 식별자 신뢰를 줄이고 서버 기준 인가 경계로 정리
```

---

## 면접 준비 범위

외울 대상은 전체 문서가 아니라 아래 문장이다.

1. N+1은 "목록 ID 수집 -> IN 절 배치 조회 -> Map 매핑" 패턴으로 설명한다. Board와 Care 수치를 대표로 말한다.
2. 동시성은 "read-modify-write가 원자적이지 않아서 DB 레벨 원자성 또는 락으로 해결했다"로 설명한다. Meetup과 PetCoin을 주 사례로 말하고 Care 확정은 보조로 둔다.
3. Location은 "초기 전체 조회를 제거하고 기본 조회를 사용자 위치/지도 중심 좌표+반경 검색으로 제한했다"로 설명한다. 지역명 검색 전략은 이후 변경 이력이 섞여 있어 본문에서 먼저 꺼내지 않는다.
4. 보안은 "Chat에서 인증과 참여자 검증은 다르고, 클라이언트가 넘긴 userId를 신뢰하지 않도록 바꿨다"로 설명한다.
5. NLP는 선택 카드다. Java-Python 계약 불일치와 silent drop을 설명할 수 있을 때만 본문에 넣는다.

면접에서 파일명이나 클래스명이 기억나지 않아도 괜찮다. 대신 문제, 원인, 해결, 검증 수치가 나와야 한다.
