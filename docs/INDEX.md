# 문서 인덱스 (자동 생성)

> ⚠️ **자동 생성 파일 — 직접 수정하지 말 것.** `python3 scripts/docs_index.py`로 재생성한다.

frontmatter가 붙은 문서 33건. 각 문서 상단에 `date/domains/type/problem/status/metric` YAML을 붙이면 자동으로 여기 잡힌다.

## 📌 이 문서를 어떻게 쓰나 — 층이 두 개다

| 층 | 파일 | 역할 |
| --- | --- | --- |
| **주장 (정본)** | [핵심성과_분석.md](핵심성과_분석.md) | 이력서·포트폴리오·면접에서 말하는 **모든 수치는 여기서만 나온다.** 수치를 고칠 때는 여기부터 고친다 |
| **증거** | 이 인덱스가 잡은 33건 | 측정·실험 기록. **통합하지 않는다** — 흩어져 있어야 "이 숫자 어디서 났나"에 링크를 던질 수 있다 |

> 과거에 같은 Chat N+1 수치가 세 군데에서 다르게 적혀 있었다(포트폴리오 `41→4`, 이 레포 `21→4`, 출처 불명 `305ms→55ms`). **표현층이 주장층보다 최신인 상태**였다. 층을 나눈 이유가 이것이다.

## 날짜순

| 날짜 | 문서 | 도메인 | 문제 | 수치 | 커밋 |
| --- | --- | --- | --- | --- | --- |
| 2026-07-15 | [board 깊은 페이지 — 2단계 지연 조인 + author_visible 비정규화, 전후 실측](analysis/board-deep-page-2026-07.md) | board | board-deep-page-lazy-join | 깊은 페이지(OFFSET 49980) 커버링 인덱스 스캔 24~32ms(비교군 66~84ms, 구코드 재현 133ms) · COUNT 단일 테이블 7~25ms(구코드 재현 22~32ms) · 너덜너덜 증명: 전체 2,500페이지 중 596페이지(23.8%)에 숨김 대상 글 유입 · k6 30s/20VU 15,555req 100% 200 · p95 63.91ms | - |
| 2026-07-14 | [쿼리 계획 감시의 일반화 — 설계 분석 (v2)](analysis/query-plan-monitoring-design.md) | board, meetup, global | query-plan-monitoring-generalization | ⚠️표본=API 1개(GET /api/boards). 그 하나에서 COUNT 쿼리 180,003행/141ms 발견(고친 목록 SELECT는 120행/4ms). 미확인: 컨트롤러 33개, Page<> COUNT 26개, 스케줄러 8개, 네이티브쿼리 20개 | - |
| 2026-07-14 | [care 도메인 쿼리 감사 — 실측 결과](analysis/query-audit/care-2026-07-14.md) | care | no-indexes-and-broken-fulltext-search | 검색 엔드포인트 HTTP 500 (FULLTEXT 인덱스 없음) · 목록/주변검색 전부 풀스캔+filesort (carerequest 인덱스 3개뿐, 전부 PK/FK) · 주변검색 선택도 208배 오판 · N+1 없음 | - |
| 2026-07-14 | [meetup 도메인 쿼리 감사 — 실측 결과](analysis/query-audit/meetup-2026-07-14.md) | meetup | unbounded-result-size-no-paging | 검색 1회 = 쿼리 51개 / 247ms (500건 무제한 반환) · 주변 1회 = 쿼리 21개 / 98ms (maxResults 기본 500) · @BatchSize 는 정상 작동 → 고전적 N+1 아님 · 공간 인덱스 정상 | - |
| 2026-07-14 | [admin 도메인 (+statistics) 쿼리 감사 — 실측 결과](analysis/query-audit/admin-2026-07-14.md) | admin, statistics, user, care | admin-care-n-plus-one-and-users-fullscan | 🔴 프로젝트 최초의 진짜 N+1 — /api/admin/care-requests 20건에 pets·첨부·백신 각 20쿼리(총 66). size 10/20/40 → 쿼리 36/66/127 로 정확히 비례 · admin 사용자 목록 users 풀스캔+filesort(10,001행) · care 검색은 admin 에서도 HTTP 500 · statistics 4개는 최대 검사 1행으로 가장 깨끗함 | - |
| 2026-07-14 | [처방 6건 적용 + 회귀 테스트 — 결과](analysis/query-audit/fixes-2026-07-14.md) | care, user, meetup, admin | top6-prescriptions-applied | 처방 1~6 적용 · care 검색 HTTP 500→200 · admin care 66→7 쿼리 · pets 155→5 쿼리 · meetup 검색 583ms→43ms · care 목록 3,060→30행 · care 주변검색 3,000행 풀스캔→208행 SPATIAL · admin 사용자목록 10,021→20행. 회귀 테스트 8개(2단계 검증 완료) | - |
| 2026-07-14 | [나머지 도메인 + 스케줄러 감사 — 실측 결과](analysis/query-audit/etc-domains-2026-07-14.md) | chat, location, user, notification, payment, report, file, statistics | unbounded-pets-endpoint-and-scheduler-fullscan | GET /api/pets/type/{type} → 7,667건 무제한 반환 + 백신 쿼리 154회 / 331ms · MeetupChatRoomRecoveryScheduler 5분마다 meetup 5,000행 풀스캔 / 0행 반환 · chat·location·payment 는 인덱스 정상 | - |
| 2026-07-14 | [전체 쿼리 감사 — 계획 및 방법론](analysis/query-audit/00-plan.md) | board, care, chat, location, meetup, notification, payment, report, user, file, statistics | full-query-audit | 감사 대상 — 컨트롤러 34개 / 엔드포인트 189개 / Page<> COUNT 30개(그중 16개는 COUNT 자동생성) / 스케줄러 9개 / nativeQuery 21곳. 2026-07-14 전 도메인 감사 완료(엔드포인트 62개 실호출). 유일 잔여: 스케줄러 8개(cron) | - |
| 2026-07-14 | [전체 쿼리 감사 — 종합](analysis/query-audit/99-summary.md) | board, care, chat, location, meetup, notification, payment, report, user, file, statistics, admin | full-query-audit-result | 전 도메인(12개) · 엔드포인트 62개 실호출 측정 완료. 처방 1~6 적용 + 회귀 테스트 8개. 치명 1건(care 검색 HTTP 500, 공개+admin 양쪽) · 진짜 N+1 1건(admin care, 20건→60쿼리) · 무제한 반환 3건 · 인덱스 부재 2곳(care, users.created_at) · statistics 는 가장 깨끗함 · 유일 잔여: 스케줄러 8개(cron) | - |
| 2026-07-14 | [board 도메인 쿼리 감사 — 실측 결과](analysis/query-audit/board-2026-07-14.md) | board | offset-deep-paging-and-autogen-count | 깊은 페이지 목록 SELECT 100,000행 검사 / 0행 반환 / 129ms (신규 발견) · 자동생성 COUNT 60,001행/호출 (users 풀스캔이 드라이빙) · N+1 없음 · 쓰기 과잉 락 없음 | - |
| 2026-07-13 | [반정규화 필드 정합성 실측 (2026-07-13)](analysis/entity-schema/evidence/denormalization-consistency-2026-07-13.md) | board, meetup, payment, location | denormalized-counter-drift | 반정규화 카운터 7종 실측 — 코드는 정합, 데이터(더미)가 오염. like_count 168/10264, comment_count 548/10264, current_participants 2791/3736 불일치 | - |
| 2026-07-13 | [쿼리 성능 baseline (2026-07-13)](analysis/entity-schema/evidence/query-baseline-2026-07-13.md) | board, location, care, meetup | query-plan-baseline | board 목록 1페이지 0.09s (전 페이지 동일 — 매번 5만행 filesort). 공간인덱스는 정상(검사행 215 vs 22,737). carerequest 주변검색은 인덱스 없음 | - |
| 2026-07-12 | [동시성/Race Condition 재검증 — 테스트 재실행 (2026-07-12)](concurrency/evidence/race-condition-reverify-2026-07-12.md) | meetup, payment, care | race-condition | PetCoin before 100→110(Lost Update 3/3 재현)→after 100→150(3/3 해결). Meetup 진짜 최초버그(a549eb33) 재현 결과는 인원초과가 아니라 Deadlock으로 인한 요청 실패(성공1/실패2, 3/3 재현) — a5943b18은 이미 Pessimistic Lock 도입된 이후였음. Care는 기존 재실행만(§4) | - |
| 2026-07-12 | [Care 요청 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/care/evidence/n-plus-one-reverify-2026-07-12.md) | care, file | n-plus-one | 101→2 queries (-98%), 511ms~617ms→133ms~137ms; file 테이블 인덱스 부재 추가 발견 | 전:[7aca5882](https://github.com/makkong1/Petory/commit/7aca5882) 후:[9c7e0d68](https://github.com/makkong1/Petory/commit/9c7e0d68) |
| 2026-07-12 | [Chat 채팅방 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/chat/evidence/n-plus-one-reverify-2026-07-12.md) | chat | n-plus-one | worktree 실측(실제 커밋 코드): 41→4 queries (-90.2%), 167ms→70ms. 재구성 테스트: 21→4 (-80.95%), 130ms→44ms | 전:[496e121a](https://github.com/makkong1/Petory/commit/496e121a) 후:[30f7e078](https://github.com/makkong1/Petory/commit/30f7e078) |
| 2026-07-12 | [Board 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/board/evidence/n-plus-one-reverify-2026-07-12.md) | board | n-plus-one | 301→3 queries (-99%), 561ms→55ms (10.2x), 21MB→3MB | 전:[3a7a581d](https://github.com/makkong1/Petory/commit/3a7a581d) 후:[19b7c120](https://github.com/makkong1/Petory/commit/19b7c120) |
| 2026-07-12 | [Location 초기 로드 재검증 — 실제 API 실측 + EXPLAIN (2026-07-12)](refactoring/location/evidence/initial-load-reverify-2026-07-12.md) | location | overfetching | worktree 실제 커밋: 22.4MB→100KB, 531.8ms→50.9ms. size=30000 트릭(검증됨, 오차<1%): 22.3MB→100KB (-99.6%), 602ms→49ms (-91.9%); DEFAULT_RADIUS_LIMIT=100 신규 발견 | 전:[5ef571d9](https://github.com/makkong1/Petory/commit/5ef571d9) 후:[162ebc14](https://github.com/makkong1/Petory/commit/162ebc14) |
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

### admin

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [admin 도메인 (+statistics) 쿼리 감사 — 실측 결과](analysis/query-audit/admin-2026-07-14.md) | admin-care-n-plus-one-and-users-fullscan | 🔴 프로젝트 최초의 진짜 N+1 — /api/admin/care-requests 20건에 pets·첨부·백신 각 20쿼리(총 66). size 10/20/40 → 쿼리 36/66/127 로 정확히 비례 · admin 사용자 목록 users 풀스캔+filesort(10,001행) · care 검색은 admin 에서도 HTTP 500 · statistics 4개는 최대 검사 1행으로 가장 깨끗함 |
| 2026-07-14 | [처방 6건 적용 + 회귀 테스트 — 결과](analysis/query-audit/fixes-2026-07-14.md) | top6-prescriptions-applied | 처방 1~6 적용 · care 검색 HTTP 500→200 · admin care 66→7 쿼리 · pets 155→5 쿼리 · meetup 검색 583ms→43ms · care 목록 3,060→30행 · care 주변검색 3,000행 풀스캔→208행 SPATIAL · admin 사용자목록 10,021→20행. 회귀 테스트 8개(2단계 검증 완료) |
| 2026-07-14 | [전체 쿼리 감사 — 종합](analysis/query-audit/99-summary.md) | full-query-audit-result | 전 도메인(12개) · 엔드포인트 62개 실호출 측정 완료. 처방 1~6 적용 + 회귀 테스트 8개. 치명 1건(care 검색 HTTP 500, 공개+admin 양쪽) · 진짜 N+1 1건(admin care, 20건→60쿼리) · 무제한 반환 3건 · 인덱스 부재 2곳(care, users.created_at) · statistics 는 가장 깨끗함 · 유일 잔여: 스케줄러 8개(cron) |

### board

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-15 | [board 깊은 페이지 — 2단계 지연 조인 + author_visible 비정규화, 전후 실측](analysis/board-deep-page-2026-07.md) | board-deep-page-lazy-join | 깊은 페이지(OFFSET 49980) 커버링 인덱스 스캔 24~32ms(비교군 66~84ms, 구코드 재현 133ms) · COUNT 단일 테이블 7~25ms(구코드 재현 22~32ms) · 너덜너덜 증명: 전체 2,500페이지 중 596페이지(23.8%)에 숨김 대상 글 유입 · k6 30s/20VU 15,555req 100% 200 · p95 63.91ms |
| 2026-07-14 | [쿼리 계획 감시의 일반화 — 설계 분석 (v2)](analysis/query-plan-monitoring-design.md) | query-plan-monitoring-generalization | ⚠️표본=API 1개(GET /api/boards). 그 하나에서 COUNT 쿼리 180,003행/141ms 발견(고친 목록 SELECT는 120행/4ms). 미확인: 컨트롤러 33개, Page<> COUNT 26개, 스케줄러 8개, 네이티브쿼리 20개 |
| 2026-07-14 | [전체 쿼리 감사 — 계획 및 방법론](analysis/query-audit/00-plan.md) | full-query-audit | 감사 대상 — 컨트롤러 34개 / 엔드포인트 189개 / Page<> COUNT 30개(그중 16개는 COUNT 자동생성) / 스케줄러 9개 / nativeQuery 21곳. 2026-07-14 전 도메인 감사 완료(엔드포인트 62개 실호출). 유일 잔여: 스케줄러 8개(cron) |
| 2026-07-14 | [전체 쿼리 감사 — 종합](analysis/query-audit/99-summary.md) | full-query-audit-result | 전 도메인(12개) · 엔드포인트 62개 실호출 측정 완료. 처방 1~6 적용 + 회귀 테스트 8개. 치명 1건(care 검색 HTTP 500, 공개+admin 양쪽) · 진짜 N+1 1건(admin care, 20건→60쿼리) · 무제한 반환 3건 · 인덱스 부재 2곳(care, users.created_at) · statistics 는 가장 깨끗함 · 유일 잔여: 스케줄러 8개(cron) |
| 2026-07-14 | [board 도메인 쿼리 감사 — 실측 결과](analysis/query-audit/board-2026-07-14.md) | offset-deep-paging-and-autogen-count | 깊은 페이지 목록 SELECT 100,000행 검사 / 0행 반환 / 129ms (신규 발견) · 자동생성 COUNT 60,001행/호출 (users 풀스캔이 드라이빙) · N+1 없음 · 쓰기 과잉 락 없음 |
| 2026-07-13 | [반정규화 필드 정합성 실측 (2026-07-13)](analysis/entity-schema/evidence/denormalization-consistency-2026-07-13.md) | denormalized-counter-drift | 반정규화 카운터 7종 실측 — 코드는 정합, 데이터(더미)가 오염. like_count 168/10264, comment_count 548/10264, current_participants 2791/3736 불일치 |
| 2026-07-13 | [쿼리 성능 baseline (2026-07-13)](analysis/entity-schema/evidence/query-baseline-2026-07-13.md) | query-plan-baseline | board 목록 1페이지 0.09s (전 페이지 동일 — 매번 5만행 filesort). 공간인덱스는 정상(검사행 215 vs 22,737). carerequest 주변검색은 인덱스 없음 |
| 2026-07-12 | [Board 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/board/evidence/n-plus-one-reverify-2026-07-12.md) | n-plus-one | 301→3 queries (-99%), 561ms→55ms (10.2x), 21MB→3MB |
| 2026-07-10 | [오버페칭 리팩토링 실측 근거 (2026-07-10)](refactoring/fetch-optimization/evidence/measurement-2026-07-10.md) | overfetching | Board 61.3→46.0ms(-25%, 바이트 불변); User 8647→5829B(-33%)/30.2→25.8ms(-15%); Care 17621→7421B(-58%)/38.3→9.9ms(-74%) |
| 2025-12-21 | [Board 도메인 성능 최적화 - 해결 완료 항목](troubleshooting/board/performance-optimization.md) | n-plus-one | 301→3 queries (-99%), 745ms→30ms (24.8x), 22.5MB→2MB |

### care

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [care 도메인 쿼리 감사 — 실측 결과](analysis/query-audit/care-2026-07-14.md) | no-indexes-and-broken-fulltext-search | 검색 엔드포인트 HTTP 500 (FULLTEXT 인덱스 없음) · 목록/주변검색 전부 풀스캔+filesort (carerequest 인덱스 3개뿐, 전부 PK/FK) · 주변검색 선택도 208배 오판 · N+1 없음 |
| 2026-07-14 | [admin 도메인 (+statistics) 쿼리 감사 — 실측 결과](analysis/query-audit/admin-2026-07-14.md) | admin-care-n-plus-one-and-users-fullscan | 🔴 프로젝트 최초의 진짜 N+1 — /api/admin/care-requests 20건에 pets·첨부·백신 각 20쿼리(총 66). size 10/20/40 → 쿼리 36/66/127 로 정확히 비례 · admin 사용자 목록 users 풀스캔+filesort(10,001행) · care 검색은 admin 에서도 HTTP 500 · statistics 4개는 최대 검사 1행으로 가장 깨끗함 |
| 2026-07-14 | [처방 6건 적용 + 회귀 테스트 — 결과](analysis/query-audit/fixes-2026-07-14.md) | top6-prescriptions-applied | 처방 1~6 적용 · care 검색 HTTP 500→200 · admin care 66→7 쿼리 · pets 155→5 쿼리 · meetup 검색 583ms→43ms · care 목록 3,060→30행 · care 주변검색 3,000행 풀스캔→208행 SPATIAL · admin 사용자목록 10,021→20행. 회귀 테스트 8개(2단계 검증 완료) |
| 2026-07-14 | [전체 쿼리 감사 — 계획 및 방법론](analysis/query-audit/00-plan.md) | full-query-audit | 감사 대상 — 컨트롤러 34개 / 엔드포인트 189개 / Page<> COUNT 30개(그중 16개는 COUNT 자동생성) / 스케줄러 9개 / nativeQuery 21곳. 2026-07-14 전 도메인 감사 완료(엔드포인트 62개 실호출). 유일 잔여: 스케줄러 8개(cron) |
| 2026-07-14 | [전체 쿼리 감사 — 종합](analysis/query-audit/99-summary.md) | full-query-audit-result | 전 도메인(12개) · 엔드포인트 62개 실호출 측정 완료. 처방 1~6 적용 + 회귀 테스트 8개. 치명 1건(care 검색 HTTP 500, 공개+admin 양쪽) · 진짜 N+1 1건(admin care, 20건→60쿼리) · 무제한 반환 3건 · 인덱스 부재 2곳(care, users.created_at) · statistics 는 가장 깨끗함 · 유일 잔여: 스케줄러 8개(cron) |
| 2026-07-13 | [쿼리 성능 baseline (2026-07-13)](analysis/entity-schema/evidence/query-baseline-2026-07-13.md) | query-plan-baseline | board 목록 1페이지 0.09s (전 페이지 동일 — 매번 5만행 filesort). 공간인덱스는 정상(검사행 215 vs 22,737). carerequest 주변검색은 인덱스 없음 |
| 2026-07-12 | [동시성/Race Condition 재검증 — 테스트 재실행 (2026-07-12)](concurrency/evidence/race-condition-reverify-2026-07-12.md) | race-condition | PetCoin before 100→110(Lost Update 3/3 재현)→after 100→150(3/3 해결). Meetup 진짜 최초버그(a549eb33) 재현 결과는 인원초과가 아니라 Deadlock으로 인한 요청 실패(성공1/실패2, 3/3 재현) — a5943b18은 이미 Pessimistic Lock 도입된 이후였음. Care는 기존 재실행만(§4) |
| 2026-07-12 | [Care 요청 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/care/evidence/n-plus-one-reverify-2026-07-12.md) | n-plus-one | 101→2 queries (-98%), 511ms~617ms→133ms~137ms; file 테이블 인덱스 부재 추가 발견 |
| 2026-07-10 | [오버페칭 리팩토링 실측 근거 (2026-07-10)](refactoring/fetch-optimization/evidence/measurement-2026-07-10.md) | overfetching | Board 61.3→46.0ms(-25%, 바이트 불변); User 8647→5829B(-33%)/30.2→25.8ms(-15%); Care 17621→7421B(-58%)/38.3→9.9ms(-74%) |
| 2026-02-28 | [펫케어 요청 목록 조회 (페이징) N+1 쿼리 문제](troubleshooting/care/care-request-paging-n-plus-one.md) | n-plus-one | 페이징 경로 applications N+1 — @BatchSize(50) 적용 완료, JOIN FETCH 대안은 미적용 |
| 2025-12-30 | [펫케어 요청 목록 조회 N+1 문제 분석](troubleshooting/care/care-request-n-plus-one-analysis.md) | n-plus-one | ~2,400→4~5 queries (-99.8%), 1084ms→66ms (-94%), 21MB→6MB |

### chat

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [나머지 도메인 + 스케줄러 감사 — 실측 결과](analysis/query-audit/etc-domains-2026-07-14.md) | unbounded-pets-endpoint-and-scheduler-fullscan | GET /api/pets/type/{type} → 7,667건 무제한 반환 + 백신 쿼리 154회 / 331ms · MeetupChatRoomRecoveryScheduler 5분마다 meetup 5,000행 풀스캔 / 0행 반환 · chat·location·payment 는 인덱스 정상 |
| 2026-07-14 | [전체 쿼리 감사 — 계획 및 방법론](analysis/query-audit/00-plan.md) | full-query-audit | 감사 대상 — 컨트롤러 34개 / 엔드포인트 189개 / Page<> COUNT 30개(그중 16개는 COUNT 자동생성) / 스케줄러 9개 / nativeQuery 21곳. 2026-07-14 전 도메인 감사 완료(엔드포인트 62개 실호출). 유일 잔여: 스케줄러 8개(cron) |
| 2026-07-14 | [전체 쿼리 감사 — 종합](analysis/query-audit/99-summary.md) | full-query-audit-result | 전 도메인(12개) · 엔드포인트 62개 실호출 측정 완료. 처방 1~6 적용 + 회귀 테스트 8개. 치명 1건(care 검색 HTTP 500, 공개+admin 양쪽) · 진짜 N+1 1건(admin care, 20건→60쿼리) · 무제한 반환 3건 · 인덱스 부재 2곳(care, users.created_at) · statistics 는 가장 깨끗함 · 유일 잔여: 스케줄러 8개(cron) |
| 2026-07-12 | [Chat 채팅방 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/chat/evidence/n-plus-one-reverify-2026-07-12.md) | n-plus-one | worktree 실측(실제 커밋 코드): 41→4 queries (-90.2%), 167ms→70ms. 재구성 테스트: 21→4 (-80.95%), 130ms→44ms |
| 2026-05-07 | [채팅방 참여자 조회 N+1 쿼리 문제](troubleshooting/chat/n-plus-one-conversationparticipant.md) | n-plus-one | 케이스B(getMyConversations) 수정 완료, 케이스A(getConversation 단건조회 반복)는 해결 방향만 제시 |
| 2025-12-20 | [채팅 메시지 읽음 처리 성능 문제](troubleshooting/chat/read-status-performance.md) | unnecessary-full-scan | 메시지 7,000건 기준 전체 조회 쿼리 1개 제거, 트랜잭션 범위 축소 |
| 2025-12-10 | [로그인 시 N+1 문제 해결](troubleshooting/users/login-n-plus-one-issue.md) | n-plus-one | 21→4 queries (-80.95%), 305ms→55ms (-81.97%), 0.58MB→0.13MB |

### file

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [나머지 도메인 + 스케줄러 감사 — 실측 결과](analysis/query-audit/etc-domains-2026-07-14.md) | unbounded-pets-endpoint-and-scheduler-fullscan | GET /api/pets/type/{type} → 7,667건 무제한 반환 + 백신 쿼리 154회 / 331ms · MeetupChatRoomRecoveryScheduler 5분마다 meetup 5,000행 풀스캔 / 0행 반환 · chat·location·payment 는 인덱스 정상 |
| 2026-07-14 | [전체 쿼리 감사 — 계획 및 방법론](analysis/query-audit/00-plan.md) | full-query-audit | 감사 대상 — 컨트롤러 34개 / 엔드포인트 189개 / Page<> COUNT 30개(그중 16개는 COUNT 자동생성) / 스케줄러 9개 / nativeQuery 21곳. 2026-07-14 전 도메인 감사 완료(엔드포인트 62개 실호출). 유일 잔여: 스케줄러 8개(cron) |
| 2026-07-14 | [전체 쿼리 감사 — 종합](analysis/query-audit/99-summary.md) | full-query-audit-result | 전 도메인(12개) · 엔드포인트 62개 실호출 측정 완료. 처방 1~6 적용 + 회귀 테스트 8개. 치명 1건(care 검색 HTTP 500, 공개+admin 양쪽) · 진짜 N+1 1건(admin care, 20건→60쿼리) · 무제한 반환 3건 · 인덱스 부재 2곳(care, users.created_at) · statistics 는 가장 깨끗함 · 유일 잔여: 스케줄러 8개(cron) |
| 2026-07-12 | [Care 요청 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/care/evidence/n-plus-one-reverify-2026-07-12.md) | n-plus-one | 101→2 queries (-98%), 511ms~617ms→133ms~137ms; file 테이블 인덱스 부재 추가 발견 |
| 2026-07-12 | [MissingPet 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/missing-pet/evidence/n-plus-one-reverify-2026-07-12.md) | n-plus-one | worktree 실측(실제 커밋 코드): 267→4 queries (-98.5%), 762ms→88ms. 재구성 테스트: 201→4 (-98%), 428ms→38ms |

### global

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [쿼리 계획 감시의 일반화 — 설계 분석 (v2)](analysis/query-plan-monitoring-design.md) | query-plan-monitoring-generalization | ⚠️표본=API 1개(GET /api/boards). 그 하나에서 COUNT 쿼리 180,003행/141ms 발견(고친 목록 SELECT는 120행/4ms). 미확인: 컨트롤러 33개, Page<> COUNT 26개, 스케줄러 8개, 네이티브쿼리 20개 |

### location

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [나머지 도메인 + 스케줄러 감사 — 실측 결과](analysis/query-audit/etc-domains-2026-07-14.md) | unbounded-pets-endpoint-and-scheduler-fullscan | GET /api/pets/type/{type} → 7,667건 무제한 반환 + 백신 쿼리 154회 / 331ms · MeetupChatRoomRecoveryScheduler 5분마다 meetup 5,000행 풀스캔 / 0행 반환 · chat·location·payment 는 인덱스 정상 |
| 2026-07-14 | [전체 쿼리 감사 — 계획 및 방법론](analysis/query-audit/00-plan.md) | full-query-audit | 감사 대상 — 컨트롤러 34개 / 엔드포인트 189개 / Page<> COUNT 30개(그중 16개는 COUNT 자동생성) / 스케줄러 9개 / nativeQuery 21곳. 2026-07-14 전 도메인 감사 완료(엔드포인트 62개 실호출). 유일 잔여: 스케줄러 8개(cron) |
| 2026-07-14 | [전체 쿼리 감사 — 종합](analysis/query-audit/99-summary.md) | full-query-audit-result | 전 도메인(12개) · 엔드포인트 62개 실호출 측정 완료. 처방 1~6 적용 + 회귀 테스트 8개. 치명 1건(care 검색 HTTP 500, 공개+admin 양쪽) · 진짜 N+1 1건(admin care, 20건→60쿼리) · 무제한 반환 3건 · 인덱스 부재 2곳(care, users.created_at) · statistics 는 가장 깨끗함 · 유일 잔여: 스케줄러 8개(cron) |
| 2026-07-13 | [반정규화 필드 정합성 실측 (2026-07-13)](analysis/entity-schema/evidence/denormalization-consistency-2026-07-13.md) | denormalized-counter-drift | 반정규화 카운터 7종 실측 — 코드는 정합, 데이터(더미)가 오염. like_count 168/10264, comment_count 548/10264, current_participants 2791/3736 불일치 |
| 2026-07-13 | [쿼리 성능 baseline (2026-07-13)](analysis/entity-schema/evidence/query-baseline-2026-07-13.md) | query-plan-baseline | board 목록 1페이지 0.09s (전 페이지 동일 — 매번 5만행 filesort). 공간인덱스는 정상(검사행 215 vs 22,737). carerequest 주변검색은 인덱스 없음 |
| 2026-07-12 | [Location 초기 로드 재검증 — 실제 API 실측 + EXPLAIN (2026-07-12)](refactoring/location/evidence/initial-load-reverify-2026-07-12.md) | overfetching | worktree 실제 커밋: 22.4MB→100KB, 531.8ms→50.9ms. size=30000 트릭(검증됨, 오차<1%): 22.3MB→100KB (-99.6%), 602ms→49ms (-91.9%); DEFAULT_RADIUS_LIMIT=100 신규 발견 |
| 2025-12-21 | [Location 도메인 초기 로드 성능 문제](troubleshooting/location/initial-load-performance.md) | overfetching | 22,699→1,026건 (-95.5%), 1484ms→700ms (-52.8%), 22MB→1MB |

### meetup

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [쿼리 계획 감시의 일반화 — 설계 분석 (v2)](analysis/query-plan-monitoring-design.md) | query-plan-monitoring-generalization | ⚠️표본=API 1개(GET /api/boards). 그 하나에서 COUNT 쿼리 180,003행/141ms 발견(고친 목록 SELECT는 120행/4ms). 미확인: 컨트롤러 33개, Page<> COUNT 26개, 스케줄러 8개, 네이티브쿼리 20개 |
| 2026-07-14 | [meetup 도메인 쿼리 감사 — 실측 결과](analysis/query-audit/meetup-2026-07-14.md) | unbounded-result-size-no-paging | 검색 1회 = 쿼리 51개 / 247ms (500건 무제한 반환) · 주변 1회 = 쿼리 21개 / 98ms (maxResults 기본 500) · @BatchSize 는 정상 작동 → 고전적 N+1 아님 · 공간 인덱스 정상 |
| 2026-07-14 | [처방 6건 적용 + 회귀 테스트 — 결과](analysis/query-audit/fixes-2026-07-14.md) | top6-prescriptions-applied | 처방 1~6 적용 · care 검색 HTTP 500→200 · admin care 66→7 쿼리 · pets 155→5 쿼리 · meetup 검색 583ms→43ms · care 목록 3,060→30행 · care 주변검색 3,000행 풀스캔→208행 SPATIAL · admin 사용자목록 10,021→20행. 회귀 테스트 8개(2단계 검증 완료) |
| 2026-07-14 | [전체 쿼리 감사 — 계획 및 방법론](analysis/query-audit/00-plan.md) | full-query-audit | 감사 대상 — 컨트롤러 34개 / 엔드포인트 189개 / Page<> COUNT 30개(그중 16개는 COUNT 자동생성) / 스케줄러 9개 / nativeQuery 21곳. 2026-07-14 전 도메인 감사 완료(엔드포인트 62개 실호출). 유일 잔여: 스케줄러 8개(cron) |
| 2026-07-14 | [전체 쿼리 감사 — 종합](analysis/query-audit/99-summary.md) | full-query-audit-result | 전 도메인(12개) · 엔드포인트 62개 실호출 측정 완료. 처방 1~6 적용 + 회귀 테스트 8개. 치명 1건(care 검색 HTTP 500, 공개+admin 양쪽) · 진짜 N+1 1건(admin care, 20건→60쿼리) · 무제한 반환 3건 · 인덱스 부재 2곳(care, users.created_at) · statistics 는 가장 깨끗함 · 유일 잔여: 스케줄러 8개(cron) |
| 2026-07-13 | [반정규화 필드 정합성 실측 (2026-07-13)](analysis/entity-schema/evidence/denormalization-consistency-2026-07-13.md) | denormalized-counter-drift | 반정규화 카운터 7종 실측 — 코드는 정합, 데이터(더미)가 오염. like_count 168/10264, comment_count 548/10264, current_participants 2791/3736 불일치 |
| 2026-07-13 | [쿼리 성능 baseline (2026-07-13)](analysis/entity-schema/evidence/query-baseline-2026-07-13.md) | query-plan-baseline | board 목록 1페이지 0.09s (전 페이지 동일 — 매번 5만행 filesort). 공간인덱스는 정상(검사행 215 vs 22,737). carerequest 주변검색은 인덱스 없음 |
| 2026-07-12 | [동시성/Race Condition 재검증 — 테스트 재실행 (2026-07-12)](concurrency/evidence/race-condition-reverify-2026-07-12.md) | race-condition | PetCoin before 100→110(Lost Update 3/3 재현)→after 100→150(3/3 해결). Meetup 진짜 최초버그(a549eb33) 재현 결과는 인원초과가 아니라 Deadlock으로 인한 요청 실패(성공1/실패2, 3/3 재현) — a5943b18은 이미 Pessimistic Lock 도입된 이후였음. Care는 기존 재실행만(§4) |
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
| 2026-07-14 | [나머지 도메인 + 스케줄러 감사 — 실측 결과](analysis/query-audit/etc-domains-2026-07-14.md) | unbounded-pets-endpoint-and-scheduler-fullscan | GET /api/pets/type/{type} → 7,667건 무제한 반환 + 백신 쿼리 154회 / 331ms · MeetupChatRoomRecoveryScheduler 5분마다 meetup 5,000행 풀스캔 / 0행 반환 · chat·location·payment 는 인덱스 정상 |
| 2026-07-14 | [전체 쿼리 감사 — 계획 및 방법론](analysis/query-audit/00-plan.md) | full-query-audit | 감사 대상 — 컨트롤러 34개 / 엔드포인트 189개 / Page<> COUNT 30개(그중 16개는 COUNT 자동생성) / 스케줄러 9개 / nativeQuery 21곳. 2026-07-14 전 도메인 감사 완료(엔드포인트 62개 실호출). 유일 잔여: 스케줄러 8개(cron) |
| 2026-07-14 | [전체 쿼리 감사 — 종합](analysis/query-audit/99-summary.md) | full-query-audit-result | 전 도메인(12개) · 엔드포인트 62개 실호출 측정 완료. 처방 1~6 적용 + 회귀 테스트 8개. 치명 1건(care 검색 HTTP 500, 공개+admin 양쪽) · 진짜 N+1 1건(admin care, 20건→60쿼리) · 무제한 반환 3건 · 인덱스 부재 2곳(care, users.created_at) · statistics 는 가장 깨끗함 · 유일 잔여: 스케줄러 8개(cron) |
| 2026-06-24 | [Notification 읽음 처리 성능 리팩토링](refactoring/notification/notification-read-performance-optimization.md) | row-by-row-update | 102→1 statement (-99%), row-by-row UPDATE → JPQL bulk UPDATE |

### payment

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [나머지 도메인 + 스케줄러 감사 — 실측 결과](analysis/query-audit/etc-domains-2026-07-14.md) | unbounded-pets-endpoint-and-scheduler-fullscan | GET /api/pets/type/{type} → 7,667건 무제한 반환 + 백신 쿼리 154회 / 331ms · MeetupChatRoomRecoveryScheduler 5분마다 meetup 5,000행 풀스캔 / 0행 반환 · chat·location·payment 는 인덱스 정상 |
| 2026-07-14 | [전체 쿼리 감사 — 계획 및 방법론](analysis/query-audit/00-plan.md) | full-query-audit | 감사 대상 — 컨트롤러 34개 / 엔드포인트 189개 / Page<> COUNT 30개(그중 16개는 COUNT 자동생성) / 스케줄러 9개 / nativeQuery 21곳. 2026-07-14 전 도메인 감사 완료(엔드포인트 62개 실호출). 유일 잔여: 스케줄러 8개(cron) |
| 2026-07-14 | [전체 쿼리 감사 — 종합](analysis/query-audit/99-summary.md) | full-query-audit-result | 전 도메인(12개) · 엔드포인트 62개 실호출 측정 완료. 처방 1~6 적용 + 회귀 테스트 8개. 치명 1건(care 검색 HTTP 500, 공개+admin 양쪽) · 진짜 N+1 1건(admin care, 20건→60쿼리) · 무제한 반환 3건 · 인덱스 부재 2곳(care, users.created_at) · statistics 는 가장 깨끗함 · 유일 잔여: 스케줄러 8개(cron) |
| 2026-07-13 | [반정규화 필드 정합성 실측 (2026-07-13)](analysis/entity-schema/evidence/denormalization-consistency-2026-07-13.md) | denormalized-counter-drift | 반정규화 카운터 7종 실측 — 코드는 정합, 데이터(더미)가 오염. like_count 168/10264, comment_count 548/10264, current_participants 2791/3736 불일치 |
| 2026-07-12 | [동시성/Race Condition 재검증 — 테스트 재실행 (2026-07-12)](concurrency/evidence/race-condition-reverify-2026-07-12.md) | race-condition | PetCoin before 100→110(Lost Update 3/3 재현)→after 100→150(3/3 해결). Meetup 진짜 최초버그(a549eb33) 재현 결과는 인원초과가 아니라 Deadlock으로 인한 요청 실패(성공1/실패2, 3/3 재현) — a5943b18은 이미 Pessimistic Lock 도입된 이후였음. Care는 기존 재실행만(§4) |

### report

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [나머지 도메인 + 스케줄러 감사 — 실측 결과](analysis/query-audit/etc-domains-2026-07-14.md) | unbounded-pets-endpoint-and-scheduler-fullscan | GET /api/pets/type/{type} → 7,667건 무제한 반환 + 백신 쿼리 154회 / 331ms · MeetupChatRoomRecoveryScheduler 5분마다 meetup 5,000행 풀스캔 / 0행 반환 · chat·location·payment 는 인덱스 정상 |
| 2026-07-14 | [전체 쿼리 감사 — 계획 및 방법론](analysis/query-audit/00-plan.md) | full-query-audit | 감사 대상 — 컨트롤러 34개 / 엔드포인트 189개 / Page<> COUNT 30개(그중 16개는 COUNT 자동생성) / 스케줄러 9개 / nativeQuery 21곳. 2026-07-14 전 도메인 감사 완료(엔드포인트 62개 실호출). 유일 잔여: 스케줄러 8개(cron) |
| 2026-07-14 | [전체 쿼리 감사 — 종합](analysis/query-audit/99-summary.md) | full-query-audit-result | 전 도메인(12개) · 엔드포인트 62개 실호출 측정 완료. 처방 1~6 적용 + 회귀 테스트 8개. 치명 1건(care 검색 HTTP 500, 공개+admin 양쪽) · 진짜 N+1 1건(admin care, 20건→60쿼리) · 무제한 반환 3건 · 인덱스 부재 2곳(care, users.created_at) · statistics 는 가장 깨끗함 · 유일 잔여: 스케줄러 8개(cron) |

### statistics

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [admin 도메인 (+statistics) 쿼리 감사 — 실측 결과](analysis/query-audit/admin-2026-07-14.md) | admin-care-n-plus-one-and-users-fullscan | 🔴 프로젝트 최초의 진짜 N+1 — /api/admin/care-requests 20건에 pets·첨부·백신 각 20쿼리(총 66). size 10/20/40 → 쿼리 36/66/127 로 정확히 비례 · admin 사용자 목록 users 풀스캔+filesort(10,001행) · care 검색은 admin 에서도 HTTP 500 · statistics 4개는 최대 검사 1행으로 가장 깨끗함 |
| 2026-07-14 | [나머지 도메인 + 스케줄러 감사 — 실측 결과](analysis/query-audit/etc-domains-2026-07-14.md) | unbounded-pets-endpoint-and-scheduler-fullscan | GET /api/pets/type/{type} → 7,667건 무제한 반환 + 백신 쿼리 154회 / 331ms · MeetupChatRoomRecoveryScheduler 5분마다 meetup 5,000행 풀스캔 / 0행 반환 · chat·location·payment 는 인덱스 정상 |
| 2026-07-14 | [전체 쿼리 감사 — 계획 및 방법론](analysis/query-audit/00-plan.md) | full-query-audit | 감사 대상 — 컨트롤러 34개 / 엔드포인트 189개 / Page<> COUNT 30개(그중 16개는 COUNT 자동생성) / 스케줄러 9개 / nativeQuery 21곳. 2026-07-14 전 도메인 감사 완료(엔드포인트 62개 실호출). 유일 잔여: 스케줄러 8개(cron) |
| 2026-07-14 | [전체 쿼리 감사 — 종합](analysis/query-audit/99-summary.md) | full-query-audit-result | 전 도메인(12개) · 엔드포인트 62개 실호출 측정 완료. 처방 1~6 적용 + 회귀 테스트 8개. 치명 1건(care 검색 HTTP 500, 공개+admin 양쪽) · 진짜 N+1 1건(admin care, 20건→60쿼리) · 무제한 반환 3건 · 인덱스 부재 2곳(care, users.created_at) · statistics 는 가장 깨끗함 · 유일 잔여: 스케줄러 8개(cron) |

### user

| 날짜 | 문서 | 문제 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [admin 도메인 (+statistics) 쿼리 감사 — 실측 결과](analysis/query-audit/admin-2026-07-14.md) | admin-care-n-plus-one-and-users-fullscan | 🔴 프로젝트 최초의 진짜 N+1 — /api/admin/care-requests 20건에 pets·첨부·백신 각 20쿼리(총 66). size 10/20/40 → 쿼리 36/66/127 로 정확히 비례 · admin 사용자 목록 users 풀스캔+filesort(10,001행) · care 검색은 admin 에서도 HTTP 500 · statistics 4개는 최대 검사 1행으로 가장 깨끗함 |
| 2026-07-14 | [처방 6건 적용 + 회귀 테스트 — 결과](analysis/query-audit/fixes-2026-07-14.md) | top6-prescriptions-applied | 처방 1~6 적용 · care 검색 HTTP 500→200 · admin care 66→7 쿼리 · pets 155→5 쿼리 · meetup 검색 583ms→43ms · care 목록 3,060→30행 · care 주변검색 3,000행 풀스캔→208행 SPATIAL · admin 사용자목록 10,021→20행. 회귀 테스트 8개(2단계 검증 완료) |
| 2026-07-14 | [나머지 도메인 + 스케줄러 감사 — 실측 결과](analysis/query-audit/etc-domains-2026-07-14.md) | unbounded-pets-endpoint-and-scheduler-fullscan | GET /api/pets/type/{type} → 7,667건 무제한 반환 + 백신 쿼리 154회 / 331ms · MeetupChatRoomRecoveryScheduler 5분마다 meetup 5,000행 풀스캔 / 0행 반환 · chat·location·payment 는 인덱스 정상 |
| 2026-07-14 | [전체 쿼리 감사 — 계획 및 방법론](analysis/query-audit/00-plan.md) | full-query-audit | 감사 대상 — 컨트롤러 34개 / 엔드포인트 189개 / Page<> COUNT 30개(그중 16개는 COUNT 자동생성) / 스케줄러 9개 / nativeQuery 21곳. 2026-07-14 전 도메인 감사 완료(엔드포인트 62개 실호출). 유일 잔여: 스케줄러 8개(cron) |
| 2026-07-14 | [전체 쿼리 감사 — 종합](analysis/query-audit/99-summary.md) | full-query-audit-result | 전 도메인(12개) · 엔드포인트 62개 실호출 측정 완료. 처방 1~6 적용 + 회귀 테스트 8개. 치명 1건(care 검색 HTTP 500, 공개+admin 양쪽) · 진짜 N+1 1건(admin care, 20건→60쿼리) · 무제한 반환 3건 · 인덱스 부재 2곳(care, users.created_at) · statistics 는 가장 깨끗함 · 유일 잔여: 스케줄러 8개(cron) |
| 2026-07-11 | [관리자 유저 검색 QueryDSL 전환 — before/after SQL 증거](refactoring/querydsl/01-before-after-sql-evidence.md) | dynamic-query-antipattern | ':param IS NULL OR' 안티패턴 제거 확인(SQL 로그). 성능 개선은 modest — LIKE/CAST가 실행계획 지배 |
| 2026-07-10 | [오버페칭 리팩토링 실측 근거 (2026-07-10)](refactoring/fetch-optimization/evidence/measurement-2026-07-10.md) | overfetching | Board 61.3→46.0ms(-25%, 바이트 불변); User 8647→5829B(-33%)/30.2→25.8ms(-15%); Care 17621→7421B(-58%)/38.3→9.9ms(-74%) |
| 2025-12-10 | [로그인 시 N+1 문제 해결](troubleshooting/users/login-n-plus-one-issue.md) | n-plus-one | 21→4 queries (-80.95%), 305ms→55ms (-81.97%), 0.58MB→0.13MB |

## 문제 유형별

### admin-care-n-plus-one-and-users-fullscan

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [admin 도메인 (+statistics) 쿼리 감사 — 실측 결과](analysis/query-audit/admin-2026-07-14.md) | admin, statistics, user, care | 🔴 프로젝트 최초의 진짜 N+1 — /api/admin/care-requests 20건에 pets·첨부·백신 각 20쿼리(총 66). size 10/20/40 → 쿼리 36/66/127 로 정확히 비례 · admin 사용자 목록 users 풀스캔+filesort(10,001행) · care 검색은 admin 에서도 HTTP 500 · statistics 4개는 최대 검사 1행으로 가장 깨끗함 |

### board-deep-page-lazy-join

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-15 | [board 깊은 페이지 — 2단계 지연 조인 + author_visible 비정규화, 전후 실측](analysis/board-deep-page-2026-07.md) | board | 깊은 페이지(OFFSET 49980) 커버링 인덱스 스캔 24~32ms(비교군 66~84ms, 구코드 재현 133ms) · COUNT 단일 테이블 7~25ms(구코드 재현 22~32ms) · 너덜너덜 증명: 전체 2,500페이지 중 596페이지(23.8%)에 숨김 대상 글 유입 · k6 30s/20VU 15,555req 100% 200 · p95 63.91ms |

### denormalized-counter-drift

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-13 | [반정규화 필드 정합성 실측 (2026-07-13)](analysis/entity-schema/evidence/denormalization-consistency-2026-07-13.md) | board, meetup, payment, location | 반정규화 카운터 7종 실측 — 코드는 정합, 데이터(더미)가 오염. like_count 168/10264, comment_count 548/10264, current_participants 2791/3736 불일치 |

### dynamic-query-antipattern

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-11 | [관리자 유저 검색 QueryDSL 전환 — before/after SQL 증거](refactoring/querydsl/01-before-after-sql-evidence.md) | user | ':param IS NULL OR' 안티패턴 제거 확인(SQL 로그). 성능 개선은 modest — LIKE/CAST가 실행계획 지배 |

### full-query-audit

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [전체 쿼리 감사 — 계획 및 방법론](analysis/query-audit/00-plan.md) | board, care, chat, location, meetup, notification, payment, report, user, file, statistics | 감사 대상 — 컨트롤러 34개 / 엔드포인트 189개 / Page<> COUNT 30개(그중 16개는 COUNT 자동생성) / 스케줄러 9개 / nativeQuery 21곳. 2026-07-14 전 도메인 감사 완료(엔드포인트 62개 실호출). 유일 잔여: 스케줄러 8개(cron) |

### full-query-audit-result

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [전체 쿼리 감사 — 종합](analysis/query-audit/99-summary.md) | board, care, chat, location, meetup, notification, payment, report, user, file, statistics, admin | 전 도메인(12개) · 엔드포인트 62개 실호출 측정 완료. 처방 1~6 적용 + 회귀 테스트 8개. 치명 1건(care 검색 HTTP 500, 공개+admin 양쪽) · 진짜 N+1 1건(admin care, 20건→60쿼리) · 무제한 반환 3건 · 인덱스 부재 2곳(care, users.created_at) · statistics 는 가장 깨끗함 · 유일 잔여: 스케줄러 8개(cron) |

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

### no-indexes-and-broken-fulltext-search

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [care 도메인 쿼리 감사 — 실측 결과](analysis/query-audit/care-2026-07-14.md) | care | 검색 엔드포인트 HTTP 500 (FULLTEXT 인덱스 없음) · 목록/주변검색 전부 풀스캔+filesort (carerequest 인덱스 3개뿐, 전부 PK/FK) · 주변검색 선택도 208배 오판 · N+1 없음 |

### offset-deep-paging-and-autogen-count

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [board 도메인 쿼리 감사 — 실측 결과](analysis/query-audit/board-2026-07-14.md) | board | 깊은 페이지 목록 SELECT 100,000행 검사 / 0행 반환 / 129ms (신규 발견) · 자동생성 COUNT 60,001행/호출 (users 풀스캔이 드라이빙) · N+1 없음 · 쓰기 과잉 락 없음 |

### overfetching

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-12 | [Location 초기 로드 재검증 — 실제 API 실측 + EXPLAIN (2026-07-12)](refactoring/location/evidence/initial-load-reverify-2026-07-12.md) | location | worktree 실제 커밋: 22.4MB→100KB, 531.8ms→50.9ms. size=30000 트릭(검증됨, 오차<1%): 22.3MB→100KB (-99.6%), 602ms→49ms (-91.9%); DEFAULT_RADIUS_LIMIT=100 신규 발견 |
| 2026-07-10 | [오버페칭 리팩토링 실측 근거 (2026-07-10)](refactoring/fetch-optimization/evidence/measurement-2026-07-10.md) | board, user, care | Board 61.3→46.0ms(-25%, 바이트 불변); User 8647→5829B(-33%)/30.2→25.8ms(-15%); Care 17621→7421B(-58%)/38.3→9.9ms(-74%) |
| 2025-12-21 | [Location 도메인 초기 로드 성능 문제](troubleshooting/location/initial-load-performance.md) | location | 22,699→1,026건 (-95.5%), 1484ms→700ms (-52.8%), 22MB→1MB |

### query-optimization

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-02-08 | [findAvailableMeetups() 성능 비교 - 리팩토링 전/후](refactoring/meetup/subquery-optimization/performance-comparison.md) | meetup | 156ms→57ms (-63.5%), 메모리 19.07MB→2.00MB (-89.5%) |

### query-plan-baseline

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-13 | [쿼리 성능 baseline (2026-07-13)](analysis/entity-schema/evidence/query-baseline-2026-07-13.md) | board, location, care, meetup | board 목록 1페이지 0.09s (전 페이지 동일 — 매번 5만행 filesort). 공간인덱스는 정상(검사행 215 vs 22,737). carerequest 주변검색은 인덱스 없음 |

### query-plan-monitoring-generalization

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [쿼리 계획 감시의 일반화 — 설계 분석 (v2)](analysis/query-plan-monitoring-design.md) | board, meetup, global | ⚠️표본=API 1개(GET /api/boards). 그 하나에서 COUNT 쿼리 180,003행/141ms 발견(고친 목록 SELECT는 120행/4ms). 미확인: 컨트롤러 33개, Page<> COUNT 26개, 스케줄러 8개, 네이티브쿼리 20개 |

### race-condition

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-12 | [동시성/Race Condition 재검증 — 테스트 재실행 (2026-07-12)](concurrency/evidence/race-condition-reverify-2026-07-12.md) | meetup, payment, care | PetCoin before 100→110(Lost Update 3/3 재현)→after 100→150(3/3 해결). Meetup 진짜 최초버그(a549eb33) 재현 결과는 인원초과가 아니라 Deadlock으로 인한 요청 실패(성공1/실패2, 3/3 재현) — a5943b18은 이미 Pessimistic Lock 도입된 이후였음. Care는 기존 재실행만(§4) |

### row-by-row-update

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-06-24 | [Notification 읽음 처리 성능 리팩토링](refactoring/notification/notification-read-performance-optimization.md) | notification | 102→1 statement (-99%), row-by-row UPDATE → JPQL bulk UPDATE |

### top6-prescriptions-applied

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [처방 6건 적용 + 회귀 테스트 — 결과](analysis/query-audit/fixes-2026-07-14.md) | care, user, meetup, admin | 처방 1~6 적용 · care 검색 HTTP 500→200 · admin care 66→7 쿼리 · pets 155→5 쿼리 · meetup 검색 583ms→43ms · care 목록 3,060→30행 · care 주변검색 3,000행 풀스캔→208행 SPATIAL · admin 사용자목록 10,021→20행. 회귀 테스트 8개(2단계 검증 완료) |

### unbounded-pets-endpoint-and-scheduler-fullscan

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [나머지 도메인 + 스케줄러 감사 — 실측 결과](analysis/query-audit/etc-domains-2026-07-14.md) | chat, location, user, notification, payment, report, file, statistics | GET /api/pets/type/{type} → 7,667건 무제한 반환 + 백신 쿼리 154회 / 331ms · MeetupChatRoomRecoveryScheduler 5분마다 meetup 5,000행 풀스캔 / 0행 반환 · chat·location·payment 는 인덱스 정상 |

### unbounded-result-size-no-paging

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [meetup 도메인 쿼리 감사 — 실측 결과](analysis/query-audit/meetup-2026-07-14.md) | meetup | 검색 1회 = 쿼리 51개 / 247ms (500건 무제한 반환) · 주변 1회 = 쿼리 21개 / 98ms (maxResults 기본 500) · @BatchSize 는 정상 작동 → 고전적 N+1 아님 · 공간 인덱스 정상 |

### unnecessary-full-scan

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2025-12-20 | [채팅 메시지 읽음 처리 성능 문제](troubleshooting/chat/read-status-performance.md) | chat | 메시지 7,000건 기준 전체 조회 쿼리 1개 제거, 트랜잭션 범위 축소 |

## 작업 성격별 (type)

### audit-plan

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [전체 쿼리 감사 — 계획 및 방법론](analysis/query-audit/00-plan.md) | board, care, chat, location, meetup, notification, payment, report, user, file, statistics | 감사 대상 — 컨트롤러 34개 / 엔드포인트 189개 / Page<> COUNT 30개(그중 16개는 COUNT 자동생성) / 스케줄러 9개 / nativeQuery 21곳. 2026-07-14 전 도메인 감사 완료(엔드포인트 62개 실호출). 유일 잔여: 스케줄러 8개(cron) |

### concurrency-evidence

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-12 | [동시성/Race Condition 재검증 — 테스트 재실행 (2026-07-12)](concurrency/evidence/race-condition-reverify-2026-07-12.md) | meetup, payment, care | PetCoin before 100→110(Lost Update 3/3 재현)→after 100→150(3/3 해결). Meetup 진짜 최초버그(a549eb33) 재현 결과는 인원초과가 아니라 Deadlock으로 인한 요청 실패(성공1/실패2, 3/3 재현) — a5943b18은 이미 Pessimistic Lock 도입된 이후였음. Care는 기존 재실행만(§4) |

### data-integrity-evidence

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-13 | [반정규화 필드 정합성 실측 (2026-07-13)](analysis/entity-schema/evidence/denormalization-consistency-2026-07-13.md) | board, meetup, payment, location | 반정규화 카운터 7종 실측 — 코드는 정합, 데이터(더미)가 오염. like_count 168/10264, comment_count 548/10264, current_participants 2791/3736 불일치 |

### design-analysis

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [쿼리 계획 감시의 일반화 — 설계 분석 (v2)](analysis/query-plan-monitoring-design.md) | board, meetup, global | ⚠️표본=API 1개(GET /api/boards). 그 하나에서 COUNT 쿼리 180,003행/141ms 발견(고친 목록 SELECT는 120행/4ms). 미확인: 컨트롤러 33개, Page<> COUNT 26개, 스케줄러 8개, 네이티브쿼리 20개 |

### performance-baseline

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-13 | [쿼리 성능 baseline (2026-07-13)](analysis/entity-schema/evidence/query-baseline-2026-07-13.md) | board, location, care, meetup | board 목록 1페이지 0.09s (전 페이지 동일 — 매번 5만행 filesort). 공간인덱스는 정상(검사행 215 vs 22,737). carerequest 주변검색은 인덱스 없음 |

### performance-evidence

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-12 | [Care 요청 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/care/evidence/n-plus-one-reverify-2026-07-12.md) | care, file | 101→2 queries (-98%), 511ms~617ms→133ms~137ms; file 테이블 인덱스 부재 추가 발견 |
| 2026-07-12 | [Chat 채팅방 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/chat/evidence/n-plus-one-reverify-2026-07-12.md) | chat | worktree 실측(실제 커밋 코드): 41→4 queries (-90.2%), 167ms→70ms. 재구성 테스트: 21→4 (-80.95%), 130ms→44ms |
| 2026-07-12 | [Board 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/board/evidence/n-plus-one-reverify-2026-07-12.md) | board | 301→3 queries (-99%), 561ms→55ms (10.2x), 21MB→3MB |
| 2026-07-12 | [Location 초기 로드 재검증 — 실제 API 실측 + EXPLAIN (2026-07-12)](refactoring/location/evidence/initial-load-reverify-2026-07-12.md) | location | worktree 실제 커밋: 22.4MB→100KB, 531.8ms→50.9ms. size=30000 트릭(검증됨, 오차<1%): 22.3MB→100KB (-99.6%), 602ms→49ms (-91.9%); DEFAULT_RADIUS_LIMIT=100 신규 발견 |
| 2026-07-12 | [MissingPet 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)](refactoring/missing-pet/evidence/n-plus-one-reverify-2026-07-12.md) | missingpet, file | worktree 실측(실제 커밋 코드): 267→4 queries (-98.5%), 762ms→88ms. 재구성 테스트: 201→4 (-98%), 428ms→38ms |
| 2026-07-11 | [모임 반경조회(nearby) k6 부하테스트 — before/after 실측 (소규모 + 대용량)](performance/performance-testing/k6/nearby-loadtest-results.md) | meetup | 소규모 p95 78.0→37.4ms(-52%); 대용량 처리량 2.11→26.7req/s(~12.6x), p95 1.75s→57.5ms(~30x) |
| 2026-07-10 | [오버페칭 리팩토링 실측 근거 (2026-07-10)](refactoring/fetch-optimization/evidence/measurement-2026-07-10.md) | board, user, care | Board 61.3→46.0ms(-25%, 바이트 불변); User 8647→5829B(-33%)/30.2→25.8ms(-15%); Care 17621→7421B(-58%)/38.3→9.9ms(-74%) |
| 2026-06-24 | [Notification 읽음 처리 성능 리팩토링](refactoring/notification/notification-read-performance-optimization.md) | notification | 102→1 statement (-99%), row-by-row UPDATE → JPQL bulk UPDATE |
| 2026-02-08 | [findAvailableMeetups() 성능 비교 - 리팩토링 전/후](refactoring/meetup/subquery-optimization/performance-comparison.md) | meetup | 156ms→57ms (-63.5%), 메모리 19.07MB→2.00MB (-89.5%) |
| 2026-02-07 | [getNearbyMeetups() 성능 비교 분석 (3단계 리팩토링)](refactoring/meetup/nearby-meetups/performance-comparison.md) | meetup | 1~3단계(B-tree bounding box): 486ms→273ms (-43.8%), 스캔행 2958→117 (-96%). 현재는 4세대 공간인덱스로 재구현됨 |
| 2026-02-07 | [findByUserIdxOrderByJoinedAtDesc() 성능 비교 분석 (Before vs After)](refactoring/meetup/participants-query/performance-comparison-participants.md) | meetup | PrepareStatement 102→2 (-98%); 실행시간은 102ms→178ms로 증가(단일쿼리 복잡화 트레이드오프) |
| 2025-12-31 | [Missing Pet 도메인 - 실제 성능 측정 결과](troubleshooting/missing-pet/performance-measurement-results.md) | missingpet | 207→3 queries (-98.5%), 571ms→79ms (-86%), 11MB→4MB (-64%) |
| 2025-12-30 | [펫케어 요청 목록 조회 N+1 문제 분석](troubleshooting/care/care-request-n-plus-one-analysis.md) | care | ~2,400→4~5 queries (-99.8%), 1084ms→66ms (-94%), 21MB→6MB |
| 2025-12-21 | [Board 도메인 성능 최적화 - 해결 완료 항목](troubleshooting/board/performance-optimization.md) | board | 301→3 queries (-99%), 745ms→30ms (24.8x), 22.5MB→2MB |
| 2025-12-21 | [Location 도메인 초기 로드 성능 문제](troubleshooting/location/initial-load-performance.md) | location | 22,699→1,026건 (-95.5%), 1484ms→700ms (-52.8%), 22MB→1MB |
| 2025-12-20 | [채팅 메시지 읽음 처리 성능 문제](troubleshooting/chat/read-status-performance.md) | chat | 메시지 7,000건 기준 전체 조회 쿼리 1개 제거, 트랜잭션 범위 축소 |
| 2025-12-10 | [로그인 시 N+1 문제 해결](troubleshooting/users/login-n-plus-one-issue.md) | chat, user | 21→4 queries (-80.95%), 305ms→55ms (-81.97%), 0.58MB→0.13MB |

### query-audit-evidence

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [care 도메인 쿼리 감사 — 실측 결과](analysis/query-audit/care-2026-07-14.md) | care | 검색 엔드포인트 HTTP 500 (FULLTEXT 인덱스 없음) · 목록/주변검색 전부 풀스캔+filesort (carerequest 인덱스 3개뿐, 전부 PK/FK) · 주변검색 선택도 208배 오판 · N+1 없음 |
| 2026-07-14 | [meetup 도메인 쿼리 감사 — 실측 결과](analysis/query-audit/meetup-2026-07-14.md) | meetup | 검색 1회 = 쿼리 51개 / 247ms (500건 무제한 반환) · 주변 1회 = 쿼리 21개 / 98ms (maxResults 기본 500) · @BatchSize 는 정상 작동 → 고전적 N+1 아님 · 공간 인덱스 정상 |
| 2026-07-14 | [admin 도메인 (+statistics) 쿼리 감사 — 실측 결과](analysis/query-audit/admin-2026-07-14.md) | admin, statistics, user, care | 🔴 프로젝트 최초의 진짜 N+1 — /api/admin/care-requests 20건에 pets·첨부·백신 각 20쿼리(총 66). size 10/20/40 → 쿼리 36/66/127 로 정확히 비례 · admin 사용자 목록 users 풀스캔+filesort(10,001행) · care 검색은 admin 에서도 HTTP 500 · statistics 4개는 최대 검사 1행으로 가장 깨끗함 |
| 2026-07-14 | [나머지 도메인 + 스케줄러 감사 — 실측 결과](analysis/query-audit/etc-domains-2026-07-14.md) | chat, location, user, notification, payment, report, file, statistics | GET /api/pets/type/{type} → 7,667건 무제한 반환 + 백신 쿼리 154회 / 331ms · MeetupChatRoomRecoveryScheduler 5분마다 meetup 5,000행 풀스캔 / 0행 반환 · chat·location·payment 는 인덱스 정상 |
| 2026-07-14 | [board 도메인 쿼리 감사 — 실측 결과](analysis/query-audit/board-2026-07-14.md) | board | 깊은 페이지 목록 SELECT 100,000행 검사 / 0행 반환 / 129ms (신규 발견) · 자동생성 COUNT 60,001행/호출 (users 풀스캔이 드라이빙) · N+1 없음 · 쓰기 과잉 락 없음 |

### query-audit-summary

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-14 | [전체 쿼리 감사 — 종합](analysis/query-audit/99-summary.md) | board, care, chat, location, meetup, notification, payment, report, user, file, statistics, admin | 전 도메인(12개) · 엔드포인트 62개 실호출 측정 완료. 처방 1~6 적용 + 회귀 테스트 8개. 치명 1건(care 검색 HTTP 500, 공개+admin 양쪽) · 진짜 N+1 1건(admin care, 20건→60쿼리) · 무제한 반환 3건 · 인덱스 부재 2곳(care, users.created_at) · statistics 는 가장 깨끗함 · 유일 잔여: 스케줄러 8개(cron) |

### query-fix-evidence

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-15 | [board 깊은 페이지 — 2단계 지연 조인 + author_visible 비정규화, 전후 실측](analysis/board-deep-page-2026-07.md) | board | 깊은 페이지(OFFSET 49980) 커버링 인덱스 스캔 24~32ms(비교군 66~84ms, 구코드 재현 133ms) · COUNT 단일 테이블 7~25ms(구코드 재현 22~32ms) · 너덜너덜 증명: 전체 2,500페이지 중 596페이지(23.8%)에 숨김 대상 글 유입 · k6 30s/20VU 15,555req 100% 200 · p95 63.91ms |
| 2026-07-14 | [처방 6건 적용 + 회귀 테스트 — 결과](analysis/query-audit/fixes-2026-07-14.md) | care, user, meetup, admin | 처방 1~6 적용 · care 검색 HTTP 500→200 · admin care 66→7 쿼리 · pets 155→5 쿼리 · meetup 검색 583ms→43ms · care 목록 3,060→30행 · care 주변검색 3,000행 풀스캔→208행 SPATIAL · admin 사용자목록 10,021→20행. 회귀 테스트 8개(2단계 검증 완료) |

### sql-evidence

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-07-11 | [관리자 유저 검색 QueryDSL 전환 — before/after SQL 증거](refactoring/querydsl/01-before-after-sql-evidence.md) | user | ':param IS NULL OR' 안티패턴 제거 확인(SQL 로그). 성능 개선은 modest — LIKE/CAST가 실행계획 지배 |

### troubleshooting

| 날짜 | 문서 | 도메인 | 수치 |
| --- | --- | --- | --- |
| 2026-05-07 | [채팅방 참여자 조회 N+1 쿼리 문제](troubleshooting/chat/n-plus-one-conversationparticipant.md) | chat | 케이스B(getMyConversations) 수정 완료, 케이스A(getConversation 단건조회 반복)는 해결 방향만 제시 |
| 2026-02-28 | [펫케어 요청 목록 조회 (페이징) N+1 쿼리 문제](troubleshooting/care/care-request-paging-n-plus-one.md) | care | 페이징 경로 applications N+1 — @BatchSize(50) 적용 완료, JOIN FETCH 대안은 미적용 |

