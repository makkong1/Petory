# Petory 알고리즘 개요

> **알고리즘의 의미**: Petory에서 "알고리즘"은 도메인별로 적용된 **핵심 비즈니스 로직, 규칙, 계산식, 상태 전이, 처리 흐름**을 의미합니다.  
> 단순 CRUD를 넘어, **결정 규칙·계산·순서·조건 분기**가 명확한 로직을 정리합니다.

---

## 1. 알고리즘 분류

| 유형 | 설명 | 예시 |
|-----|------|------|
| **계산 알고리즘** | 수학적 공식/계산식 | Haversine 거리 계산, 인기도 점수 |
| **검색 알고리즘** | 조회·필터링·정렬 규칙 | 지역 계층 검색, 반경 검색 |
| **상태 전이** | 상태 머신·전이 조건 | Care OPEN→IN_PROGRESS→COMPLETED |
| **규칙 기반** | 조건→결과 매핑 | 경고 3회→이용제한 3일 |
| **병합/집계** | 다중 소스 통합·요약 | Redis+MySQL 알림 병합, 일별 통계 |
| **동시성 제어** | 원자적 연산·락 | 에스크로 비관적 락, 인원 증가 |

---

## 2. 도메인별 알고리즘 목록

| 도메인 | 핵심 알고리즘 | 문서 |
|--------|---------------|------|
| **Location** | Haversine 거리 계산, 지역 계층 검색, ST_Distance_Sphere 반경 검색 | [위치-알고리즘.md](./location/위치-알고리즘.md) |
| **Care** | 상태 전이(OPEN→IN_PROGRESS→COMPLETED/CANCELLED), 에스크로 연동 | [케어-알고리즘.md](./care/케어-알고리즘.md) |
| **Payment** | 에스크로 HOLD→RELEASED/REFUNDED, 비관적 락 | [펫코인-알고리즘.md](./payment/펫코인-알고리즘.md) |
| **Meetup** | Haversine 반경 검색, 인원 원자적 증가, 상태 전이 | [모임-알고리즘.md](./meetup/모임-알고리즘.md) |
| **Board** | 인기 게시글 점수, 스냅샷 조회 우선순위 | [커뮤니티-알고리즘.md](./board/커뮤니티-알고리즘.md) |
| **User/Report** | 경고 3회→이용제한 3일, 만료 자동 해제 | [제재-알고리즘.md](./user/제재-알고리즘.md) |
| **Statistics** | 일별 집계(Daily Summary), 배치 스케줄러 | [통계-알고리즘.md](./statistics/통계-알고리즘.md) |
| **Notification** | Redis+MySQL 병합, 최신 50개 TTL 24h | [알림-알고리즘.md](./notification/알림-알고리즘.md) |

---

## 3. 참고 문서

- [전체 아키텍처](../architecture/전체%20아키텍처.md)
- [트랜잭션 & 동시성 사례](../concurrency/transaction-concurrency-cases.md)
- [예외처리 분석](../refactoring/exception/00-exception-analysis-overview.md)
