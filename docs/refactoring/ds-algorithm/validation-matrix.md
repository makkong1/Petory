# 자료구조/알고리즘 개선 후보 검증 매트릭스

## 목적

Petory 백엔드 도메인에서 발견한 자료구조/알고리즘 개선 후보를 바로 "문제"로 단정하지 않고, 코드 근거와 테스트/측정으로 검증한다.

이 문서는 다음 흐름을 관리한다.

1. 현재 코드에서 후보를 찾는다.
2. 후보를 문제, 확장성 리스크, 단순 리팩토링, 면접 포인트로 분류한다.
3. 재현 테스트 또는 성능 비교 테스트를 만든다.
4. 결과가 확인된 항목만 리팩토링/트러블슈팅/포트폴리오 문서에 반영한다.

---

## 문서 위치 판단


| 위치                                                   | 역할       | 사용 기준                                              |
| ---------------------------------------------------- | -------- | -------------------------------------------------- |
| `docs/interview/ds-algorithm-checklist.md`           | 면접용 요약   | 설명/답변 연습 중심. 현재 `docs/interview/`는 `.gitignore` 대상 |
| `docs/refactoring/ds-algorithm/validation-matrix.md` | 검증 백로그   | 개선 후보, 테스트 계획, 측정 결과, 판정 관리                        |
| `docs/troubleshooting/{domain}/...`                  | 실제 문제 기록 | 테스트/운영 로그로 버그, 성능 저하, 동시성 문제가 재현된 경우               |
| `docs/refactoring/{domain}/...`                      | 개선 기록    | 동작은 맞지만 구조, 성능, 확장성, 유지보수성을 개선한 경우                 |
| 포트폴리오 리팩토링/트러블슈팅 페이지                                 | 외부 공개 요약 | 검증 완료 항목만 Before/After와 근거 수치 중심으로 반영              |


**원칙**: 포트폴리오에는 "개선 가능성"만으로 올리지 않는다. 재현 테스트, EXPLAIN, 쿼리 수, 실행 시간, 메모리, 동시성 결과 중 하나 이상의 근거가 있어야 한다.

---

## 판정 기준


| 판정      | 의미                                                       | 포트폴리오 반영                      |
| ------- | -------------------------------------------------------- | ----------------------------- |
| 실제 문제   | 테스트나 측정에서 잘못된 결과, 중복, 누락, race condition, 과도한 쿼리/지연이 재현됨 | 트러블슈팅 페이지에 반영                 |
| 확장성 리스크 | 현재 데이터에서는 정상이나 데이터 증가 시 비용 증가가 명확함                       | 리팩토링 페이지에 "확장성 개선"으로 반영       |
| 개선 후보   | 기능 문제는 없지만 자료구조, 타입 안정성, 쿼리 전략 개선 여지가 있음                 | 리팩토링 백로그로 유지. 개선 후 수치가 있으면 반영 |
| 면접 포인트  | 실제 리팩토링보다 설명 가치가 큼                                       | 면접 문서에 유지                     |
| 문제 아님   | 코드/테스트 확인 결과 현재 선택이 적합함                                  | "왜 현재 방식이 맞는지"로 정리            |


---

## 테스트 유형


| 유형         | 사용 대상                                                 | 권장 방식                      | 테스트 파일 성격   |
| ---------- | ----------------------------------------------------- | -------------------------- | ----------- |
| 정확성 테스트    | 정렬, 중복 제거, 점수 계산, 상태 전이                               | JUnit 단위 테스트               | 빠르게 반복 가능   |
| 동시성 테스트    | 참가 인원, 잔액, 상태 변경, 중복 조회수                              | `CountDownLatch` 기반 통합 테스트 | MySQL 필요    |
| DB 성능 검증   | Offset pagination, Spatial query, GROUP BY 집계         | EXPLAIN + 대량 seed + 통합 테스트 | 문서/로그 보관 필요 |
| 알고리즘 성능 비교 | `List.contains` vs `HashSet.contains`, in-memory sort | JMH 또는 제한적 JUnit benchmark | 수치 흔들림 주의   |
| 구조 검증      | N+1 방지, batch map 구성, 캐시 정책                           | 쿼리 카운트/코드 근거               | 필요 시 통합 테스트 |


**주의**: 프로덕션 코드에 baseline/optimized 두 버전을 같이 넣지 않는다. 순수 알고리즘 비교는 테스트 코드 내부 helper로만 비교한다.

---

## 전체 검증 매트릭스


| Domain            | 후보                                | 유형               | 현재 근거                                                              | 검증 방식                                 | 판정 기준                                 | 상태           |
| ----------------- | --------------------------------- | ---------------- | ------------------------------------------------------------------ | ------------------------------------- | ------------------------------------- | ------------ |
| board             | Offset pagination 비용              | 확장성 리스크          | `Pageable`, `PageRequest` 기반 목록 조회                                 | 대량 데이터 + `EXPLAIN` + offset별 응답 시간 비교 | offset 증가에 따라 rows/latency 증가         | 미검증          |
| board             | 캐시 정책 불일치                         | 개선 후보            | `@Cacheable` 제거 이력, `@CacheEvict` 잔존                               | 캐시 설정/서비스 어노테이션 대조                    | 사용하지 않는 캐시 설정 또는 evict 잔재 확인          | 미검증          |
| board             | batch Map으로 반응/파일 조회              | 문제 아님 또는 면접 포인트  | `Map<Long, List<FileDTO>>`, `Map<Long, Map<ReactionType, Long>>`   | 쿼리 수 비교 또는 코드 근거 정리                   | N개 게시글에서도 파일/반응 조회가 batch로 고정         | 미검증          |
| chat              | cursor/offset 혼재                  | 개선 후보            | 메시지 조회에 `Pageable`, createdAt cursor 계열 쿼리 존재                      | API별 파라미터/쿼리 매핑 확인                    | 무한스크롤 API가 offset이면 cursor 전환 후보      | 미검증          |
| chat              | unread count 원자성                  | 실제 문제 또는 문제 아님   | `unreadCount = unreadCount + 1`, `unreadCount = 0` update query 존재 | 동시 메시지/읽음 처리 테스트                      | unread count 누락/음수/불일치 발생 여부          | 미검증          |
| location          | Spatial index 활용                  | 실제 문제 또는 문제 아님   | `ST_Within`, `ST_Distance_Sphere` 반경 검색                            | MySQL `EXPLAIN` 확인                    | spatial index 미사용 또는 rows 과다이면 문제     | 미검증          |
| location          | sort 문자열 분기                       | 개선 후보            | `:sort` 문자열로 `score`, `stable`, `reviews`, `rating` 분기             | 허용값 테스트 + 잘못된 sort 입력 테스트             | 오타/미지원 값이 의도한 fallback으로 가는지          | 미검증          |
| location          | score 전체 재계산                      | 확장성 리스크          | `findAll()` 후 전체 `saveAll()` 스케줄러                                  | 데이터 N 증가별 실행 시간 측정                    | 전체 row 증가에 따라 배치 시간이 운영 한계 초과         | 미검증          |
| petRecommendation | 태그 매칭 `List.contains`             | 알고리즘 개선 후보       | `intentTags.stream().filter(locationTags::contains)`               | List vs HashSet 비교 테스트                | 태그 수 증가 시 HashSet lookup 비용이 낮음       | 테스트 통과       |
| petRecommendation | 추천 점수 정렬 정확성                      | 정확성              | 가중합 후 `finalScore DESC` 정렬                                         | 고정 fixture로 점수/순위 테스트                 | 예상 순위와 실제 순위 일치                       | 테스트 통과       |
| care              | 상태 전이 검증                          | 실제 문제 또는 개선 후보   | `transitionTo`가 일부 상태만 특별 처리                                       | 불가능 전이 테스트                            | `COMPLETED -> OPEN` 같은 전이가 허용되면 개선 후보 | 미검증          |
| care              | 만료 스케줄러 스캔                        | 확장성 리스크          | `@Scheduled`, status/date 조건 조회                                    | EXPLAIN + 대량 데이터                      | status/date 인덱스 미사용 또는 rows 과다        | 미검증          |
| meetup            | 참가 인원 원자적 증가                      | 문제 아님 검증         | `currentParticipants < maxParticipants` 조건부 UPDATE                 | 동시 참가 테스트                             | maxParticipants 초과 미발생                | 미검증          |
| meetup            | nearby 검색 실행 계획                   | 확장성 리스크          | `ST_Within`, `ST_Distance_Sphere`, `LIMIT`                         | EXPLAIN + 대량 데이터                      | spatial index/조건 인덱스 활용 여부            | 미검증          |
| file              | attachment batch grouping         | 문제 아님 또는 면접 포인트  | `groupingBy(targetIdx)`                                            | 여러 target fixture로 정확성 테스트            | target별 파일 목록 누락/중복 없음                | 미검증          |
| statistics        | todayStats 캐시 무효화                 | 개선 후보            | `@Cacheable("todayStats")`, `recordPayment()` 즉시 반영                | 결제 기록 후 today snapshot 재조회 테스트        | 캐시 때문에 최신 통계가 stale이면 실제 문제           | 미검증          |
| statistics        | 주/월 집계 stream sum                 | 문제 아님 또는 확장성 리스크 | 일별 통계 list를 Java stream으로 합산                                       | 기간별 row 수와 실행 시간 확인                   | 일/주/월 단위 row 수가 작으면 문제 아님             | 미검증          |
| notification      | Redis + DB 병합 dedup               | 정확성              | Redis id `Set<Long>`으로 DB 알림 필터링                                   | Redis/DB 중복 fixture 테스트               | 중복 제거 + createdAt 정렬 유지               | 미검증          |
| notification      | Redis value List 최신 50개           | 정확성              | `notifications.add(0)`, `subList(0, 50)`                           | 51개 이상 저장 테스트                         | 최신 50개만 유지, 순서 보존                     | 미검증          |
| payment           | 비관적 락 잔액 처리                       | 문제 아님 검증         | `findByIdForUpdate`, `PESSIMISTIC_WRITE`                           | 기존 race condition 테스트 보강              | 동시 차감 후 잔액 불일치 없음                     | 일부 기존 테스트 있음 |
| payment           | 멱등성 키 부재                          | 개선 후보            | 결제 요청 idempotency key 별도 확인 필요                                     | 중복 요청 시나리오 테스트                        | 같은 요청이 중복 차감/중복 에스크로 생성되면 실제 문제       | 미검증          |
| report            | Java `groupingBy/counting` 관리자 집계 | 확장성 리스크          | 조회 결과를 Map으로 target별 카운팅                                           | 데이터 N별 실행 시간/메모리 측정                   | 신고 수 증가 시 관리자 조회 지연                   | 미검증          |
| user              | Access Token 즉시 무효화 미구현           | 설계 트레이드오프        | 로그아웃은 Refresh Token 제거 중심                                          | 로그아웃 후 기존 Access Token 호출 테스트         | 기존 Access Token이 TTL까지 유효하면 설계상 한계    | 미검증          |
| user              | 이메일 인증 Redis TTL                  | 정확성              | Redis 인증 상태 저장/삭제                                                  | TTL/회원가입 후 삭제 테스트                     | 만료/삭제가 계약대로 동작                        | 미검증          |
| admin             | 동적 필터 조합                          | 개선 후보            | 관리자 조회 조건 조합                                                       | 필터 조합별 결과 테스트                         | 조건 누락/메모리 필터링 발견 시 개선                 | 미검증          |


---

## 도메인별 테스트 파일 계획

### 1. petRecommendation


| 목적                       | 추천 파일                                                                                                              | 방식                                  |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------ | ----------------------------------- |
| 태그 매칭 List vs HashSet 비교 | `backend/test/java/com/linkup/Petory/domain/petRecommendation/service/PetRecommendTagMatchingPerformanceTest.java` | 테스트 내부 baseline/optimized helper 비교 |
| 점수 계산 정확성                | `backend/test/java/com/linkup/Petory/domain/petRecommendation/service/PetRecommendScoreCalculatorTest.java`        | 고정 DTO fixture로 점수/이유/정렬 검증         |


우선순위가 높은 이유: DB 없이 순수 Java로 검증 가능하고, 면접에서 시간복잡도 설명이 명확하다.

검증 결과:


| 항목           | 결과  | 근거                                                                                        |
| ------------ | --- | ----------------------------------------------------------------------------------------- |
| 태그 매칭 결과 동일성 | 통과  | `List.contains` 방식과 `HashSet.contains` 방식의 matched count 동일                               |
| 태그 매칭 비용 비교  | 통과  | 1,000개 location tag / 10개 intent tag fixture에서 List 순차 비교 비용이 HashSet lookup 비용보다 100배 초과 |
| 점수 계산        | 통과  | 가중합 결과, 추천 이유(`nearby`, `high_rating`, `many_reviews`, `popular`, `tag_match:`*) 검증       |
| 추천 순위        | 통과  | `finalScore DESC` 정렬 결과가 기대 순서와 일치                                                        |


실행 명령:

```bash
./gradlew test --tests 'com.linkup.Petory.domain.petRecommendation.service.PetRecommendScoreCalculatorTest' --tests 'com.linkup.Petory.domain.petRecommendation.service.PetRecommendTagMatchingPerformanceTest'
```

### 2. meetup


| 목적              | 추천 파일                                                      | 방식                     |
| --------------- | ---------------------------------------------------------- | ---------------------- |
| 참가 인원 초과 방지     | 기존 `MeetupServiceRaceConditionTest` 보강                     | `CountDownLatch` 동시 참가 |
| nearby 검색 실행 계획 | `docs/refactoring/meetup/nearby-meetups/` 하위 EXPLAIN 문서 보강 | MySQL EXPLAIN          |


우선순위가 높은 이유: 동시성은 포트폴리오 트러블슈팅 소재로 가장 강하다. 단, 이미 해결된 항목이면 "문제 아님 검증"으로 정리한다.

### 3. notification


| 목적                | 추천 파일                                                                                              | 방식                                                   |
| ----------------- | -------------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| Redis/DB 병합 중복 제거 | `backend/test/java/com/linkup/Petory/domain/notification/service/NotificationMergeTest.java`       | 서비스 helper가 private이면 public API 또는 리플렉션 대신 시나리오 테스트 |
| 최신 50개 유지         | `backend/test/java/com/linkup/Petory/domain/notification/service/NotificationRedisWindowTest.java` | Redis 필요. 통합 테스트로 분리                                 |


주의: Redis가 필요한 테스트는 로컬 Redis 없으면 실패한다. 단위 테스트로 만들 수 없으면 통합 테스트로 명확히 분리한다.

### 4. board


| 목적                   | 추천 파일/문서                                                                              | 방식                                 |
| -------------------- | ------------------------------------------------------------------------------------- | ---------------------------------- |
| offset pagination 비용 | `docs/refactoring/board/board-pagination-explain.md`                                  | 대량 seed + EXPLAIN + 응답 시간          |
| 조회수 중복 방지            | `backend/test/java/com/linkup/Petory/domain/board/service/BoardViewLogDedupTest.java` | 같은 사용자 반복 조회 시 insert ignore 결과 확인 |
| batch Map 조회         | 기존 `BoardPerformanceComparisonTest` 보강                                                | 쿼리 수/fixture 결과 비교                 |


주의: offset pagination은 JUnit만으로 설득력이 약하다. EXPLAIN과 대량 데이터 측정값이 필요하다.

### 5. statistics


| 목적                     | 추천 파일                                                                                        | 방식                                             |
| ---------------------- | -------------------------------------------------------------------------------------------- | ---------------------------------------------- |
| todayStats 캐시 stale 여부 | `backend/test/java/com/linkup/Petory/domain/statistics/StatisticsCacheInvalidationTest.java` | `getTodaySnapshot()` 후 `recordPayment()` 후 재조회 |
| 주/월 집계 정확성             | 기존 `StatisticsSchedulerTest` 보강                                                              | 일별 fixture 합산 결과 검증                            |


포트폴리오 후보: 캐시 stale이 재현되면 트러블슈팅, 재현되지 않으면 캐시 전략 설명으로만 유지.

### 6. report


| 목적                   | 추천 파일                                                                                    | 방식                                     |
| -------------------- | ---------------------------------------------------------------------------------------- | -------------------------------------- |
| target별 신고 count 정확성 | `backend/test/java/com/linkup/Petory/domain/report/service/ReportGroupingCountTest.java` | targetType + targetIdx별 grouping 결과 검증 |
| 관리자 목록 확장성           | `docs/refactoring/report/report-admin-counting-performance.md`                           | 데이터 N별 실행 시간/메모리 측정                    |


주의: 현재는 원자적 증가 문제가 아니다. Java grouping 비용이 확장성 리스크인지 확인하는 쪽이다.

### 7. user


| 목적                      | 추천 파일                                                  | 방식           |
| ----------------------- | ------------------------------------------------------ | ------------ |
| 로그아웃 후 Access Token 유효성 | 기존 `AuthServiceTest` 또는 신규 `AuthTokenInvalidationTest` | 현재 한계 확인 테스트 |
| 이메일 인증 Redis TTL        | 기존 이메일 인증 테스트가 없으면 `EmailVerificationServiceRedisTest` | Redis 통합 테스트 |


포트폴리오 후보: "JWT 한계와 Refresh Token 무효화 전략"으로 리팩토링/보안 설계 페이지에 적합하다.

---

## 테스트 작성 원칙

1. 먼저 현재 코드의 동작을 고정한다.
2. 실패를 기대하는 테스트와 성능 비교 테스트를 섞지 않는다.
3. 성능 수치는 절대 단일 실행으로 판단하지 않는다.
4. 성능 비교는 같은 데이터, 같은 JVM, 같은 DB 상태에서 3회 이상 측정한다.
5. 테스트명에 의도를 드러낸다.

예시:

```java
@Test
void tagMatching_hashSetLookupScalesBetterThanListContains() {
    // baseline: List.contains
    // optimized: HashSet.contains
    // assert: 결과 count는 동일해야 하고, 큰 입력에서 HashSet 쪽이 더 낮은 비용이어야 한다.
}
```

동시성 테스트는 다음 형태를 기본으로 한다.

```java
CountDownLatch ready = new CountDownLatch(threadCount);
CountDownLatch start = new CountDownLatch(1);
CountDownLatch done = new CountDownLatch(threadCount);
```

---

## 포트폴리오 반영 기준

포트폴리오의 리팩토링/트러블슈팅 페이지에는 아래 조건을 만족한 항목만 추가한다.


| 페이지       | 추가 조건                                        | 표현 방식                                |
| --------- | -------------------------------------------- | ------------------------------------ |
| 트러블슈팅     | 재현 테스트가 실패하거나, 기존 코드에서 실제 데이터 불일치/성능 저하가 확인됨 | 문제 상황 → 원인 → 재현 테스트 → 해결 → 결과        |
| 리팩토링      | 기능은 정상이나 구조/성능 개선 전후 수치가 있음                  | Before/After → 선택한 자료구조/알고리즘 → 측정 결과 |
| 도메인 상세    | 검증된 핵심 설계 포인트                                | "왜 이 구조를 선택했는가" 중심                   |
| 면접/CS 페이지 | 실제 코드와 연결된 개념 설명                             | 시간복잡도, 인덱스, 캐시, 동시성 트레이드오프           |


반영하면 안 되는 표현:

- "성능이 개선될 것이다"처럼 측정 없는 주장
- "문제가 있다"처럼 재현 없는 단정
- 현재 코드에 없는 Redis blacklist, message queue, 원자적 counter 같은 과장 표현

반영 가능한 표현:

- "대량 데이터 기준 offset 증가에 따라 latency가 증가해 cursor pagination을 검토했다"
- "동시 참가 테스트에서 조건부 UPDATE가 최대 인원 초과를 막는 것을 확인했다"
- "태그 수가 커질 때 `List.contains`는 O(n*m), `HashSet` 변환은 O(n+m) 구조라 비교 테스트 대상으로 잡았다"

---

## 진행 순서

1. ✅ `petRecommendation` 순수 알고리즘 테스트 작성
2. `meetup` 동시성 테스트 보강
3. `notification` 병합/dedup 정확성 테스트 작성
4. `statistics` 캐시 stale 여부 확인
5. `board`/`location`은 대량 데이터와 EXPLAIN 기반으로 별도 측정
6. 검증 완료 항목만 도메인별 리팩토링/트러블슈팅 문서로 승격

