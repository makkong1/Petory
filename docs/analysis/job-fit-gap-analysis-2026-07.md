# 채용공고 대비 Petory 기술 스택 갭 분석

> 작성일: 2026-07-11
> 기준 브랜치: dev
> 목적: 백엔드 채용공고(집품 플랫폼, Spring/Java) 요구사항과 Petory 실제 구현 상태를 대조하여 학습 우선순위 도출

---

## 1. 이미 충족 — 근거 있음 (✅)

| 요구사항                                    | 근거 (파일/경로)                                                                                                                                                               |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Spring(Java) 개발 경험                      | Spring Boot 3.5.7, 8개 도메인 × controller/service/entity/repository 4-layer 구조                                                                                              |
| RDBMS 활용 + 구조 설계                      | MySQL, 도메인별 `@Index` 6곳 (`AdminAuditLog`, `SystemConfig`, `LoginEvent`, `UserPetIntentSignal` 등)                                                                         |
| JPA/Hibernate                               | 전 레이어 JPA 기반, `@Query`(JPQL/native) 31곳                                                                                                                                 |
| 캐시 활용 성능개선                          | Redis 3용도 — 알림 캐시(최신 50개, TTL 24h) / 게시글 상세 캐시(`@Cacheable`) / 이메일 인증 임시 저장(TTL 24h)                                                                  |
| 동시성 제어 (우대 이상 스펙)                | 비관적 락 4곳 — `SpringDataJpaConversationRepository`, `SpringDataJpaPetCoinEscrowRepository`, `SpringDataJpaUsersRepository`, `SpringDataJpaMeetupRepository`                 |
| **EXPLAIN 실행계획 튜닝 (우대사항 정조준)** | `docs/refactoring/meetup/subquery-optimization/explain-results.md`, `docs/refactoring/meetup/nearby-meetups/explain-queries.sql`, 최근 커밋 `d8fcfc1b`(explain 쿼리 결과 추가) |
| 로그·성능 분석 리팩터링                     | `docs/refactoring/` 다수 — 최근 "목록 오버페칭 2차 점검" 커밋(`0c667815`)처럼 반복적 성능 점검 이력                                                                            |
| 테스트 코드 작성                            | `backend/test/` 61개 파일, CI에서 실제 MySQL/Redis 서비스 기동 후 통합 테스트 실행                                                                                             |
| 공공데이터 활용 경험 (우대)                 | `location` 도메인 — `PublicDataLocationService`, `PublicDataLocationDTO`, `AdminLocationController`                                                                            |

## 2. 부족 — 학습/보강 필요 (❌ 또는 부분적)

| 요구사항                        | 현재 상태                                                                                     | 우선순위     |
| ------------------------------- | --------------------------------------------------------------------------------------------- | ------------ |
| **QueryDSL**                    | `build.gradle`에 의존성 없음. JPQL/native `@Query`만 사용                                     | 🔴 높음      |
| **모니터링(Grafana)**           | `micrometer-registry-prometheus` 의존성만 존재, 실제 대시보드/알림 구성 없음                  | 🔴 높음      |
| **AWS 인프라 실사용**           | `docker-compose.yml` mysql 볼륨 주석에 "RDS 이전 고려" TODO만 존재, 실제 배포 흔적 없음       | 🔴 높음      |
| Sentry/Datadog 실시간 오류 분석 | 전무                                                                                          | 🟡 중간      |
| PostgreSQL                      | MySQL만 사용 (공고는 "MySQL 혹은 PostgreSQL"이라 필수 아님)                                   | 🟡 중간      |
| MongoDB / Elasticsearch         | 전무                                                                                          | 🟢 낮음~중간 |
| Airflow / IaC                   | 전무 (배치는 `@Scheduled`로만 처리 — `StatisticsScheduler`, `MeetupScheduler` 등 8곳)         | 🟢 낮음      |
| 24/365 무중단 서비스 경험       | 실운영 트래픽 없음 (사이드프로젝트) — 대체 불가, blue-green 등 설계 경험으로 스토리 대체 필요 | —            |

## 3. 로컬 개발 기준 추천 (비용 0, ROI 순)

기존 인프라(`docker-compose.yml`, `build.gradle`)에 이미 발판이 있는 항목부터.

### Tier 1 — 즉시 적용 가능, 기존 인프라에 얹기만 하면 됨

1. **QueryDSL 도입**
   `build.gradle`에 의존성 추가 후 동적 조건 많은 쿼리(검색 필터, 목록 조회) 1~2개만 전환. 전체 전환 불필요 — "왜 이건 QueryDSL, 저건 JPQL로 남겼는가" 판단 기준을 설명할 수 있으면 충분.

2. **Prometheus + Grafana를 docker-compose에 추가**
   `micrometer-registry-prometheus`가 이미 메트릭을 노출 중이므로 시각화 계층만 없는 상태. 컨테이너 2개 추가로 완결. "DB 커넥션 풀 고갈 알림" 같은 규칙 하나 걸면 "모니터링으로 문제 감지" 스토리 완성.

3. **로컬 부하테스트(k6)로 EXPLAIN 튜닝 수치화**
   `docs/refactoring/meetup/subquery-optimization/explain-results.md`에 튜닝 전/후 실제 TPS·응답시간이 빠져 있음. k6로 로컬 MySQL에 부하를 쏴서 기존 강점(EXPLAIN 튜닝)을 숫자로 완성 — 새로 배우기보다 기존 자산 보강이라 ROI 가장 높음.

### Tier 2 — 여유 있을 때

4. **LocalStack + Terraform**
   실제 AWS 비용 없이 S3/RDS 로컬 에뮬레이션. `docker-compose.yml`의 "RDS 이전 고려" TODO를 Terraform 코드로 실체화하면 "AWS 인프라"와 "IaC" 두 요건을 비용 없이 동시에 커버. 단, 실제 클라우드 배포 경험과는 면접에서 구분해서 설명할 것.

5. **Elasticsearch를 docker-compose에 추가**
   board/meetup처럼 검색 비중 높은 도메인에 붙여 "MySQL LIKE 검색 → ES 전환, 응답시간 개선" 스토리 확보. 매핑/analyzer 학습 곡선이 있어 Tier 1보다 시간 소요 큼.

### 후순위 (현재는 스킵 권장)

- **Airflow**: 컨테이너 구성 비용(webserver+scheduler+메타DB) 대비, 이미 `@Scheduled` 배치가 잘 구조화돼 있어 얻는 어필 포인트가 적음. 데이터엔지니어링 직군이 아니면 우선순위 낮음.
- **Sentry**: 무료 클라우드 티어(로컬은 아니지만 비용 0). Grafana 알림으로 유사 스토리 커버 가능하므로 Tier 2 이후.
- **PostgreSQL**: 공고 필수 요건 아님. MySQL 튜닝 경험의 전이 가능성으로 설명하는 편이 새로 파는 것보다 효율적.

## 4. 결론

1. QueryDSL 도입
2. Prometheus+Grafana 연결
3. 기존 EXPLAIN 튜닝 문서에 k6 부하테스트 수치 보강
4. (여유 시) LocalStack+Terraform, Elasticsearch

이미 보유한 EXPLAIN 튜닝·동시성 제어(락) 이력은 공고의 우대사항과 정확히 겹치므로, 새 항목을 넓히기보다 **면접에서 구체적 수치(before/after 쿼리 비용)로 설명 가능하게 정리**하는 것이 최우선.
