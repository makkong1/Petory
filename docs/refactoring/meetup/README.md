# Meetup 도메인 리팩토링 문서

## 📋 개요

Meetup 도메인의 성능 최적화 및 코드 개선 작업을 정리한 문서입니다.

---

## 📁 폴더 구조

### 1. `nearby-meetups/` - 반경 기반 모임 조회 최적화

**성격**: 성능 최적화 리팩토링  
**문제**: 인메모리 필터링으로 인한 성능 저하  
**해결**: DB 쿼리로 이동 + 인덱스 활용 (Bounding Box)

**주요 개선**:
- 인메모리 필터링 제거
- DB 쿼리로 필터링 이동
- Bounding Box 방식으로 인덱스 활용
- 스캔 행 수 96% 감소 (2958개 → 117개)

**문서**:
- `performance-comparison.md` - Before/After 성능 비교
- `index-analysis.md` - 인덱스 분석 및 최적화 결과
- `explain-queries.sql` - EXPLAIN 실행 계획 확인용 SQL

---

### 2. `participants-query/` - 참여자 조회 N+1 쿼리 해결

**성격**: 트러블슈팅 (N+1 쿼리 문제 해결)  
**문제**: JOIN FETCH 없이 연관 엔티티 조회로 인한 N+1 쿼리 발생  
**해결**: JOIN FETCH 적용

**주요 개선**:
- N+1 쿼리 완전 제거
- PrepareStatement 수 98% 감소 (102개 → 2개)
- 네트워크 라운드트립 대폭 감소

**문서**:
- `performance-comparison-participants.md` - Before/After 성능 비교
- `performance-results-participants-before.md` - 리팩토링 전 측정 결과
- `performance-results-participants-after.md` - 리팩토링 후 측정 결과

---

## 🔍 리팩토링 vs 트러블슈팅 구분

### 리팩토링 (Refactoring)
- **목적**: 코드 구조 개선, 성능 최적화, 가독성 향상
- **특징**: 기능 변경 없이 내부 구현 개선
- **예시**: `nearby-meetups/` - 인메모리 필터링을 DB 쿼리로 변경

### 트러블슈팅 (Troubleshooting)
- **목적**: 특정 문제(버그, 성능 이슈) 해결
- **특징**: 문제 발생 → 원인 분석 → 해결
- **예시**: `participants-query/` - N+1 쿼리 문제 해결

**현재 상황**:
- `nearby-meetups/`: **리팩토링** (성능 최적화를 위한 구조 개선)
- `participants-query/`: **트러블슈팅** (N+1 쿼리 문제 해결)

하지만 둘 다 성능 최적화 작업이므로 `refactoring` 폴더에 포함하는 것도 적절합니다.

---

## 📊 전체 성능 개선 요약

| 항목 | 개선 내용 | 개선율 |
|------|----------|--------|
| **반경 기반 조회** | 스캔 행 수 감소 | 96% 감소 |
| **참여자 조회** | 쿼리 수 감소 | 98% 감소 |

---

## 🔗 관련 문서

- [백엔드 성능 최적화 문서](./backend-performance-optimization.md)
- [프론트엔드 성능 최적화 문서](./frontend-performance-optimization.md)
- [리팩토링 요약](./refactoring-summary.md)
