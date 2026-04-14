# Refactoring Skill

## 트리거

사용자가 리팩토링, 코드 개선, 성능 최적화를 요청할 때 실행한다.
보통 `/review` (Code Review) 이후에 사용된다.

## 리팩토링 타입 분류

모든 리팩토링은 아래 3가지 타입 중 하나로 분류한다:

### Type 1: 구조 개선 (Structure)
- 아키텍처 일관성, 레이어 분리, 책임 분리
- 예: private `@Transactional` → 별도 빈 분리, 도메인 간 의존 방향 수정

### Type 2: 성능 개선 (Performance)
- 쿼리 수 감소, 응답 시간 단축, 메모리 절약
- 예: N+1 해결, `findAll()` → 페이징, 인덱스 추가

### Type 3: 가독성 (Readability)
- 코드 명확성, 네이밍, 불필요 코드 제거
- 예: DTO → record, `System.out.println` → Logger, 미사용 import 정리

## 동작 절차

### 1단계: 리팩토링 대상 확인

- 코드 리뷰 결과가 있으면 Critical/Warning 항목을 우선 처리한다.
- 없으면 사용자가 지정한 파일/도메인을 분석한다.

### 2단계: 리팩토링 계획 수립

변경 전에 **타입 + 측정 기준 포함** 계획을 보여준다:

```
## 리팩토링 계획

### 1. [파일명] - N+1 쿼리 해결
- **타입**: 🚀 Performance
- **Before**: 목록 10건 조회 시 쿼리 11회 (1 + N)
- **After**: FETCH JOIN으로 쿼리 1회
- **측정 기준**: 쿼리 수 11 → 1 (90% 감소)
- **영향 파일**: Repository 1개, Service 0개

### 2. [파일명] - private @Transactional 분리
- **타입**: 🏗️ Structure
- **Before**: self-invocation으로 트랜잭션 미적용
- **After**: 별도 빈으로 분리하여 프록시 경유
- **측정 기준**: 트랜잭션 정상 작동 보장
- **영향 파일**: 신규 빈 1개, 기존 Service 1개 수정

### 3. [파일명] - DTO record 변환
- **타입**: 📖 Readability
- **Before**: class + getter 30줄
- **After**: record 5줄
- **측정 기준**: 코드량 83% 감소
- **영향 파일**: DTO 1개, 사용처 0개 (호환)

→ 진행할까? (전부 / 번호 선택 / 수정)
```

### 3단계: 적용

사용자 확인 후 코드를 수정한다.

### 4단계: 검증

- `./gradlew compileJava` (백엔드 변경 시)
- 린터 에러 확인
- 변경 요약:

```
## 리팩토링 완료

| # | 파일 | 타입 | Before | After |
|---|------|------|--------|-------|
| 1 | CareRequestRepository | 🚀 Perf | 쿼리 11회 | 쿼리 1회 |
| 2 | CareRequestService | 🏗️ Structure | self-invocation | 별도 빈 |
| 3 | CareResponseDTO | 📖 Readability | class 30줄 | record 5줄 |

→ `/test` 실행을 권장한다.
```

### 5단계: 문서화 제안

리팩토링 완료 후 `docs/refactoring/<domain>/`에 기록을 남길 때, **아래 순서를 반드시 지킨다** (코드 리뷰 문서 `*-code-review-*.md`와 분리해도 됨).

```
## [제목] 리팩토링 기록

### 발생 위치
- 파일·클래스·메서드 (필요 시 라인)

### 문제 / 개선점
- 한두 문장

### 개선 코드 (요지)
- Before/After 또는 핵심 스니펫

### 상태
- **개선 완료** (날짜) / **개선 불필요** / **미적용** (사유)
```

- 성능 관련이면 Before/After에 쿼리 수·응답 시간 등 측정값을 넣는다.
- 동일 배치에 여러 항목이면 위 블록을 **항목별로 반복**한다.

## 이 프로젝트의 리팩토링 규칙

### DTO → record 변환
- Response DTO → record 우선
- Request DTO → Jackson 역직렬화 확인 후 적용
- 필드 10개 이상 + 빌더 필수인 경우는 제외

### 쿼리 최적화
- `LIKE '%…%'` → FULLTEXT `MATCH...AGAINST` 또는 접두사 `LIKE '…%'`
- N+1 → FETCH JOIN 또는 `@BatchSize`
- 불필요한 `findAll()` → 페이징 또는 조건 쿼리

### 동시성
- 금액/수량 → 비관적 락 또는 원자적 증가 쿼리
- `@Transactional` 범위 최소화
- private 메서드의 `@Transactional` → 별도 빈으로 분리

### 레거시 정리
- `RestTemplate` → `RestClient`
- `System.out.println` → SLF4J Logger
- 하드코딩된 Role 문자열 → `RoleConstants` / `Role` enum

## 워크플로우 연계

- `/review` Critical 항목 → `/refactor` 로 이어짐
- 리팩토링 완료 → `/test` 실행 권장
- 테스트 통과 → `/commit` 제안
- 성능 개선 시 → `/docs` 리팩토링 기록 제안

## 제약

- 한 번에 너무 많은 파일을 수정하지 않는다 (최대 5~7개).
- 기능 변경 없이 구조만 개선한다 (refactor ≠ feat).
- 수정 전 반드시 사용자 확인을 받는다.
- 테스트가 있는 코드를 수정하면 테스트도 함께 확인한다.
