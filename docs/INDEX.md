<!-- 자동 생성 파일 — 직접 수정하지 말 것. `python3 scripts/docs_index.py`로 재생성 -->
# 문서 인덱스 (자동 생성)

frontmatter가 붙은 문서 21건. 각 문서 상단에 `date/domains/type/problem/status/metric` YAML을 붙이면 자동으로 여기 잡힌다.

## 날짜순

| 날짜 | 문서 | 도메인 | 문제 | 수치 | 커밋 |
| --- | --- | --- | --- | --- | --- |
| 2026-07-12 | [동시성/Race Condition 재검증 — 테스트 재실행 (2026-07-12)](concurrency/evidence/race-condition-reverify-2026-07-12.md) | meetup, payment, care | race-condition | Meetup 3명 제한 정확히 지켜짐(2성공+1실패), PetCoin 100→150 정확히 누적(Lost Update 없음), Care stuck state 없이 OPEN→IN_PROGRESS 정상 전이 | - |
| 2026-07-12 | [Care 요청 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/care/evidence/n-plus-one-reverify-2026-07-12.md) | care, file | n-plus-one | 101→2 queries (-98%), 511ms~617ms→133ms~137ms; file 테이블 인덱스 부재 추가 발견 | 전:[7aca5882](https://github.com/makkong1/Petory/commit/7aca5882) 후:[9c7e0d68](https://github.com/makkong1/Petory/commit/9c7e0d68) |
| 2026-07-12 | [Chat 채팅방 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/chat/evidence/n-plus-one-reverify-2026-07-12.md) | chat | n-plus-one | worktree 실측(실제 커밋 코드): 41→4 queries (-90.2%), 167ms→70ms. 재구성 테스트: 21→4 (-80.95%), 130ms→44ms | 전:[496e121a](https://github.com/makkong1/Petory/commit/496e121a) 후:[30f7e078](https://github.com/makkong1/Petory/commit/30f7e078) |
| 2026-07-12 | [Board 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/board/evidence/n-plus-one-reverify-2026-07-12.md) | board | n-plus-one | 301→3 queries (-99%), 561ms→55ms (10.2x), 21MB→3MB | 전:[3a7a581d](https://github.com/makkong1/Petory/commit/3a7a581d) 후:[19b7c120](https://github.com/makkong1/Petory/commit/19b7c120) |
| 2026-07-12 | [Location 초기 로드 재검증 — 실제 API 실측 + EXPLAIN (2026-07-12)](refactoring/location/evidence/initial-load-reverify-2026-07-12.md) | location | overfetching | 22.3MB→100KB (-99.6%), 602ms→49ms (-91.9%) HTTP 실측; DEFAULT_RADIUS_LIMIT=100 신규 발견 | 전:[5ef571d9](https://github.com/makkong1/Petory/commit/5ef571d9) 후:[162ebc14](https://github.com/makkong1/Petory/commit/162ebc14) |
| 2026-07-12 | [MissingPet 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/missing-pet/evidence/n-plus-one-reverify-2026-07-12.md) | missingpet, file | n-plus-one | worktree 실측(실제 커밋 코드): 267→4 queries (-98.5%), 762ms→88ms. 재구성 테스트: 201→4 (-98%), 428ms→38ms | 전:[9c7e0d68](https://github.com/makkong1/Petory/commit/9c7e0d68) 후:[9dbf85ba](https://github.com/makkong1/Petory/commit/9dbf85ba) |
| 2026-07-11 | [관리자 유저 검색 QueryDSL 전환 — before/after SQL 증거](refactoring/querydsl/01-before-after-sql-evidence.md) | user | dynamic-query-antipattern | ':param IS NULL OR' 안티패턴 제거 확인(SQL 로그). 성능 개선은 modest — LIKE/CAST가 실행계획 지배 | - |
| 2026-07-11 | [모임 반경조회(nearby) k6 부하테스트 — before/after 실측 (소규모 + 대용량)](performance/performance-testing/k6/nearby-loadtest-results.md) | meetup | in-memory-filtering | 소규모 p95 78.0→37.4ms(-52%); 대용량 처리량 2.11→26.7req/s(~12.6x), p95 1.75s→57.5ms(~30x) | - |
| 2026-07-10 | [오버페칭 리팩토링 실측 근거 (2026-07-10)](refactoring/fetch-optimization/evidence/measurement-2026-07-10.md) | board, user, care | overfetching | Board 61.3→46.0ms(-25%, 바이트 불변); User 8647→5829B(-33%)/30.2→25.8ms(-15%); Care 17621→7421B(-58%)/38.3→9.9ms(-74%) | - |
| 2026-06-24 | [Notification 읽음 처리 성능 리팩토링](refactoring/notification/notification-read-performance-optimization.md) | notification | row-by-row-update | 102→1 statement (-99%), row-by-row UPDATE → JPQL bulk UPDATE | - |
| 2026-05-07 | [채팅방 참여자 조회 N+1 쿼리 문제](troubleshooting/chat/n-plus-one-conversationparticipant.md) | chat | n-plus-one | 케이스B(getMyConversations) 수정 완료, 케이스A(getConversation 단건조회 반복)는 해결 방향만 제시 | - |
| 2026-02-28 | [펫케어 요청 목록 조회 (페이징) N+1 쿼리 문제](troubleshooting/care/care-request-paging-n-plus-one.md) | care | n-plus-one | 페이징 경로 applications N+1 — @BatchSize(50) 적용 완료, JOIN FETCH 대안은 미적용 | - |
| 2026-02-08 | [findAvailableMeetups() 성능 비교 - 리팩토링 전/후](refactoring/meetup/subquery-optimization/performance-comparison.md) | meetup | query-optimization | 156ms→57ms (-63.5%), 메모리 19.07MB→2.00MB (-89.5%) | - |
| 2026-02-07 | [getNearbyMeetups() 성능 비교 분석 (3단계 리팩토링)](refactoring/meetup/nearby-meetups/performance-comparison.md) | meetup | in-memory-filtering | 1~3단계(B-tree bounding box): 486ms→273ms (-43.8%), 스캔행 2958→117 (-96%). 현재는 4세대 공간인덱스로 재구현됨 | - |
| 2026-02-07 | [findByUserIdxOrderByJoinedAtDesc() 성능 비교 분석 (Before vs After)](refactoring/meetup/participants-query/performance-comparison-participants.md) | meetup | n-plus-one | PrepareStatement 102→2 (-98%); 실행시간은 102ms→178ms로 증가(단일쿼리 복잡화 트레이드오프) | - |
| 2025-12-31 | [Missing Pet 도메인 - 실제 성능 측정 결과](troubleshooting/missing-pet/performance-measurement-results.md) | missingpet | n-plus-one | 207→3 queries (-98.5%), 571ms→79ms (-86%), 11MB→4MB (-64%) | - |
| 2025-12-30 | [펫케어 요청 목록 조회 N+1 문제 분석](troubleshooting/care/care-request-n-plus-one-analysis.md) | care | n-plus-one | ~2,400→4~5 queries (-99.8%), 1084ms→66ms (-94%), 21MB→6MB | - |
| 2025-12-21 | [Board 도메인 성능 최적화 - 해결 완료 항목](troubleshooting/board/performance-optimization.md) | board | n-plus-one | 301→3 queries (-99%), 745ms→30ms (24.8x), 22.5MB→2MB | - |
| 2025-12-21 | [Location 도메인 초기 로드 성능 문제](troubleshooting/location/initial-load-performance.md) | location | overfetching | 22,699→1,026건 (-95.5%), 1484ms→700ms (-52.8%), 22MB→1MB | - |
| 2025-12-20 | [채팅 메시지 읽음 처리 성능 문제](troubleshooting/chat/read-status-performance.md) | chat | unnecessary-full-scan | 메시지 7,000건 기준 전체 조회 쿼리 1개 제거, 트랜잭션 범위 축소 | - |
| 2025-12-10 | [로그인 시 N+1 문제 해결](troubleshooting/users/login-n-plus-one-issue.md) | chat, user | n-plus-one | 21→4 queries (-80.95%), 305ms→55ms (-81.97%), 0.58MB→0.13MB | - |

## 도메인별

### board

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-12 | [Board 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/board/evidence/n-plus-one-reverify-2026-07-12.md) | n-plus-one | 301→3 queries (-99%), 561ms→55ms (10.2x), 21MB→3MB |
| 2026-07-10 | [오버페칭 리팩토링 실측 근거 (2026-07-10)](refactoring/fetch-optimization/evidence/measurement-2026-07-10.md) | overfetching | Board 61.3→46.0ms(-25%, 바이트 불변); User 8647→5829B(-33%)/30.2→25.8ms(-15%); Care 17621→7421B(-58%)/38.3→9.9ms(-74%) |
| 2025-12-21 | [Board 도메인 성능 최적화 - 해결 완료 항목](troubleshooting/board/performance-optimization.md) | n-plus-one | 301→3 queries (-99%), 745ms→30ms (24.8x), 22.5MB→2MB |

### care

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-12 | [동시성/Race Condition 재검증 — 테스트 재실행 (2026-07-12)](concurrency/evidence/race-condition-reverify-2026-07-12.md) | race-condition | Meetup 3명 제한 정확히 지켜짐(2성공+1실패), PetCoin 100→150 정확히 누적(Lost Update 없음), Care stuck state 없이 OPEN→IN_PROGRESS 정상 전이 |
| 2026-07-12 | [Care 요청 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/care/evidence/n-plus-one-reverify-2026-07-12.md) | n-plus-one | 101→2 queries (-98%), 511ms~617ms→133ms~137ms; file 테이블 인덱스 부재 추가 발견 |
| 2026-07-10 | [오버페칭 리팩토링 실측 근거 (2026-07-10)](refactoring/fetch-optimization/evidence/measurement-2026-07-10.md) | overfetching | Board 61.3→46.0ms(-25%, 바이트 불변); User 8647→5829B(-33%)/30.2→25.8ms(-15%); Care 17621→7421B(-58%)/38.3→9.9ms(-74%) |
| 2026-02-28 | [펫케어 요청 목록 조회 (페이징) N+1 쿼리 문제](troubleshooting/care/care-request-paging-n-plus-one.md) | n-plus-one | 페이징 경로 applications N+1 — @BatchSize(50) 적용 완료, JOIN FETCH 대안은 미적용 |
| 2025-12-30 | [펫케어 요청 목록 조회 N+1 문제 분석](troubleshooting/care/care-request-n-plus-one-analysis.md) | n-plus-one | ~2,400→4~5 queries (-99.8%), 1084ms→66ms (-94%), 21MB→6MB |

### chat

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-12 | [Chat 채팅방 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/chat/evidence/n-plus-one-reverify-2026-07-12.md) | n-plus-one | worktree 실측(실제 커밋 코드): 41→4 queries (-90.2%), 167ms→70ms. 재구성 테스트: 21→4 (-80.95%), 130ms→44ms |
| 2026-05-07 | [채팅방 참여자 조회 N+1 쿼리 문제](troubleshooting/chat/n-plus-one-conversationparticipant.md) | n-plus-one | 케이스B(getMyConversations) 수정 완료, 케이스A(getConversation 단건조회 반복)는 해결 방향만 제시 |
| 2025-12-20 | [채팅 메시지 읽음 처리 성능 문제](troubleshooting/chat/read-status-performance.md) | unnecessary-full-scan | 메시지 7,000건 기준 전체 조회 쿼리 1개 제거, 트랜잭션 범위 축소 |
| 2025-12-10 | [로그인 시 N+1 문제 해결](troubleshooting/users/login-n-plus-one-issue.md) | n-plus-one | 21→4 queries (-80.95%), 305ms→55ms (-81.97%), 0.58MB→0.13MB |

### file

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-12 | [Care 요청 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/care/evidence/n-plus-one-reverify-2026-07-12.md) | n-plus-one | 101→2 queries (-98%), 511ms~617ms→133ms~137ms; file 테이블 인덱스 부재 추가 발견 |
| 2026-07-12 | [MissingPet 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/missing-pet/evidence/n-plus-one-reverify-2026-07-12.md) | n-plus-one | worktree 실측(실제 커밋 코드): 267→4 queries (-98.5%), 762ms→88ms. 재구성 테스트: 201→4 (-98%), 428ms→38ms |

### location

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-12 | [Location 초기 로드 재검증 — 실제 API 실측 + EXPLAIN (2026-07-12)](refactoring/location/evidence/initial-load-reverify-2026-07-12.md) | overfetching | 22.3MB→100KB (-99.6%), 602ms→49ms (-91.9%) HTTP 실측; DEFAULT_RADIUS_LIMIT=100 신규 발견 |
| 2025-12-21 | [Location 도메인 초기 로드 성능 문제](troubleshooting/location/initial-load-performance.md) | overfetching | 22,699→1,026건 (-95.5%), 1484ms→700ms (-52.8%), 22MB→1MB |

### meetup

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-12 | [동시성/Race Condition 재검증 — 테스트 재실행 (2026-07-12)](concurrency/evidence/race-condition-reverify-2026-07-12.md) | race-condition | Meetup 3명 제한 정확히 지켜짐(2성공+1실패), PetCoin 100→150 정확히 누적(Lost Update 없음), Care stuck state 없이 OPEN→IN_PROGRESS 정상 전이 |
| 2026-07-11 | [모임 반경조회(nearby) k6 부하테스트 — before/after 실측 (소규모 + 대용량)](performance/performance-testing/k6/nearby-loadtest-results.md) | in-memory-filtering | 소규모 p95 78.0→37.4ms(-52%); 대용량 처리량 2.11→26.7req/s(~12.6x), p95 1.75s→57.5ms(~30x) |
| 2026-02-08 | [findAvailableMeetups() 성능 비교 - 리팩토링 전/후](refactoring/meetup/subquery-optimization/performance-comparison.md) | query-optimization | 156ms→57ms (-63.5%), 메모리 19.07MB→2.00MB (-89.5%) |
| 2026-02-07 | [getNearbyMeetups() 성능 비교 분석 (3단계 리팩토링)](refactoring/meetup/nearby-meetups/performance-comparison.md) | in-memory-filtering | 1~3단계(B-tree bounding box): 486ms→273ms (-43.8%), 스캔행 2958→117 (-96%). 현재는 4세대 공간인덱스로 재구현됨 |
| 2026-02-07 | [findByUserIdxOrderByJoinedAtDesc() 성능 비교 분석 (Before vs After)](refactoring/meetup/participants-query/performance-comparison-participants.md) | n-plus-one | PrepareStatement 102→2 (-98%); 실행시간은 102ms→178ms로 증가(단일쿼리 복잡화 트레이드오프) |

### missingpet

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-12 | [MissingPet 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/missing-pet/evidence/n-plus-one-reverify-2026-07-12.md) | n-plus-one | worktree 실측(실제 커밋 코드): 267→4 queries (-98.5%), 762ms→88ms. 재구성 테스트: 201→4 (-98%), 428ms→38ms |
| 2025-12-31 | [Missing Pet 도메인 - 실제 성능 측정 결과](troubleshooting/missing-pet/performance-measurement-results.md) | n-plus-one | 207→3 queries (-98.5%), 571ms→79ms (-86%), 11MB→4MB (-64%) |

### notification

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-06-24 | [Notification 읽음 처리 성능 리팩토링](refactoring/notification/notification-read-performance-optimization.md) | row-by-row-update | 102→1 statement (-99%), row-by-row UPDATE → JPQL bulk UPDATE |

### payment

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-12 | [동시성/Race Condition 재검증 — 테스트 재실행 (2026-07-12)](concurrency/evidence/race-condition-reverify-2026-07-12.md) | race-condition | Meetup 3명 제한 정확히 지켜짐(2성공+1실패), PetCoin 100→150 정확히 누적(Lost Update 없음), Care stuck state 없이 OPEN→IN_PROGRESS 정상 전이 |

### user

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-11 | [관리자 유저 검색 QueryDSL 전환 — before/after SQL 증거](refactoring/querydsl/01-before-after-sql-evidence.md) | dynamic-query-antipattern | ':param IS NULL OR' 안티패턴 제거 확인(SQL 로그). 성능 개선은 modest — LIKE/CAST가 실행계획 지배 |
| 2026-07-10 | [오버페칭 리팩토링 실측 근거 (2026-07-10)](refactoring/fetch-optimization/evidence/measurement-2026-07-10.md) | overfetching | Board 61.3→46.0ms(-25%, 바이트 불변); User 8647→5829B(-33%)/30.2→25.8ms(-15%); Care 17621→7421B(-58%)/38.3→9.9ms(-74%) |
| 2025-12-10 | [로그인 시 N+1 문제 해결](troubleshooting/users/login-n-plus-one-issue.md) | n-plus-one | 21→4 queries (-80.95%), 305ms→55ms (-81.97%), 0.58MB→0.13MB |

## 문제 유형별

### dynamic-query-antipattern

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-11 | [관리자 유저 검색 QueryDSL 전환 — before/after SQL 증거](refactoring/querydsl/01-before-after-sql-evidence.md) | user | ':param IS NULL OR' 안티패턴 제거 확인(SQL 로그). 성능 개선은 modest — LIKE/CAST가 실행계획 지배 |

### in-memory-filtering

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-11 | [모임 반경조회(nearby) k6 부하테스트 — before/after 실측 (소규모 + 대용량)](performance/performance-testing/k6/nearby-loadtest-results.md) | meetup | 소규모 p95 78.0→37.4ms(-52%); 대용량 처리량 2.11→26.7req/s(~12.6x), p95 1.75s→57.5ms(~30x) |
| 2026-02-07 | [getNearbyMeetups() 성능 비교 분석 (3단계 리팩토링)](refactoring/meetup/nearby-meetups/performance-comparison.md) | meetup | 1~3단계(B-tree bounding box): 486ms→273ms (-43.8%), 스캔행 2958→117 (-96%). 현재는 4세대 공간인덱스로 재구현됨 |

### n-plus-one

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-12 | [Care 요청 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/care/evidence/n-plus-one-reverify-2026-07-12.md) | care, file | 101→2 queries (-98%), 511ms~617ms→133ms~137ms; file 테이블 인덱스 부재 추가 발견 |
| 2026-07-12 | [Chat 채팅방 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/chat/evidence/n-plus-one-reverify-2026-07-12.md) | chat | worktree 실측(실제 커밋 코드): 41→4 queries (-90.2%), 167ms→70ms. 재구성 테스트: 21→4 (-80.95%), 130ms→44ms |
| 2026-07-12 | [Board 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/board/evidence/n-plus-one-reverify-2026-07-12.md) | board | 301→3 queries (-99%), 561ms→55ms (10.2x), 21MB→3MB |
| 2026-07-12 | [MissingPet 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/missing-pet/evidence/n-plus-one-reverify-2026-07-12.md) | missingpet, file | worktree 실측(실제 커밋 코드): 267→4 queries (-98.5%), 762ms→88ms. 재구성 테스트: 201→4 (-98%), 428ms→38ms |
| 2026-05-07 | [채팅방 참여자 조회 N+1 쿼리 문제](troubleshooting/chat/n-plus-one-conversationparticipant.md) | chat | 케이스B(getMyConversations) 수정 완료, 케이스A(getConversation 단건조회 반복)는 해결 방향만 제시 |
| 2026-02-28 | [펫케어 요청 목록 조회 (페이징) N+1 쿼리 문제](troubleshooting/care/care-request-paging-n-plus-one.md) | care | 페이징 경로 applications N+1 — @BatchSize(50) 적용 완료, JOIN FETCH 대안은 미적용 |
| 2026-02-07 | [findByUserIdxOrderByJoinedAtDesc() 성능 비교 분석 (Before vs After)](refactoring/meetup/participants-query/performance-comparison-participants.md) | meetup | PrepareStatement 102→2 (-98%); 실행시간은 102ms→178ms로 증가(단일쿼리 복잡화 트레이드오프) |
| 2025-12-31 | [Missing Pet 도메인 - 실제 성능 측정 결과](troubleshooting/missing-pet/performance-measurement-results.md) | missingpet | 207→3 queries (-98.5%), 571ms→79ms (-86%), 11MB→4MB (-64%) |
| 2025-12-30 | [펫케어 요청 목록 조회 N+1 문제 분석](troubleshooting/care/care-request-n-plus-one-analysis.md) | care | ~2,400→4~5 queries (-99.8%), 1084ms→66ms (-94%), 21MB→6MB |
| 2025-12-21 | [Board 도메인 성능 최적화 - 해결 완료 항목](troubleshooting/board/performance-optimization.md) | board | 301→3 queries (-99%), 745ms→30ms (24.8x), 22.5MB→2MB |
| 2025-12-10 | [로그인 시 N+1 문제 해결](troubleshooting/users/login-n-plus-one-issue.md) | chat, user | 21→4 queries (-80.95%), 305ms→55ms (-81.97%), 0.58MB→0.13MB |

### overfetching

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-12 | [Location 초기 로드 재검증 — 실제 API 실측 + EXPLAIN (2026-07-12)](refactoring/location/evidence/initial-load-reverify-2026-07-12.md) | location | 22.3MB→100KB (-99.6%), 602ms→49ms (-91.9%) HTTP 실측; DEFAULT_RADIUS_LIMIT=100 신규 발견 |
| 2026-07-10 | [오버페칭 리팩토링 실측 근거 (2026-07-10)](refactoring/fetch-optimization/evidence/measurement-2026-07-10.md) | board, user, care | Board 61.3→46.0ms(-25%, 바이트 불변); User 8647→5829B(-33%)/30.2→25.8ms(-15%); Care 17621→7421B(-58%)/38.3→9.9ms(-74%) |
| 2025-12-21 | [Location 도메인 초기 로드 성능 문제](troubleshooting/location/initial-load-performance.md) | location | 22,699→1,026건 (-95.5%), 1484ms→700ms (-52.8%), 22MB→1MB |

### query-optimization

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-02-08 | [findAvailableMeetups() 성능 비교 - 리팩토링 전/후](refactoring/meetup/subquery-optimization/performance-comparison.md) | meetup | 156ms→57ms (-63.5%), 메모리 19.07MB→2.00MB (-89.5%) |

### race-condition

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-12 | [동시성/Race Condition 재검증 — 테스트 재실행 (2026-07-12)](concurrency/evidence/race-condition-reverify-2026-07-12.md) | meetup, payment, care | Meetup 3명 제한 정확히 지켜짐(2성공+1실패), PetCoin 100→150 정확히 누적(Lost Update 없음), Care stuck state 없이 OPEN→IN_PROGRESS 정상 전이 |

### row-by-row-update

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-06-24 | [Notification 읽음 처리 성능 리팩토링](refactoring/notification/notification-read-performance-optimization.md) | notification | 102→1 statement (-99%), row-by-row UPDATE → JPQL bulk UPDATE |

### unnecessary-full-scan

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2025-12-20 | [채팅 메시지 읽음 처리 성능 문제](troubleshooting/chat/read-status-performance.md) | chat | 메시지 7,000건 기준 전체 조회 쿼리 1개 제거, 트랜잭션 범위 축소 |

