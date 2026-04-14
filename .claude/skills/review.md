# Code Review Skill

## 트리거

사용자가 코드 리뷰, 점검, 검토를 요청할 때 실행한다.

## 동작 절차

### 1단계: 범위 파악

- 사용자가 지정한 파일/도메인/PR 범위를 확인한다.
- 범위가 없으면 최근 변경 파일(`git diff --name-only HEAD~1`)을 대상으로 한다.

### 2단계: 룰 기반 자동 체크

변경된 파일을 읽고 **반드시 아래 체크리스트를 전부 통과**시킨다. 하나라도 위반이면 Finding으로 보고한다.

#### 체크리스트 A: JPA / 쿼리

| # | 룰 | 위반 시 등급 |
|---|-----|------------|
| A1 | `@ManyToOne` / `@OneToOne`에 `fetch = FetchType.LAZY` 명시 | Critical |
| A2 | 컬렉션 연관을 루프 안에서 접근 시 FETCH JOIN 또는 `@BatchSize` 존재 | Critical |
| A3 | `LIKE '%…%'` → FULLTEXT `MATCH...AGAINST` 또는 접두사 `LIKE '…%'` 권장 | Warning |
| A4 | `findAll()` 호출 → Pageable 파라미터 또는 조건 쿼리로 대체 필요 | Warning |
| A5 | Repository 커스텀 쿼리에 소프트 삭제 조건(`deleted = false`) 포함 | Critical |

#### 체크리스트 B: 트랜잭션 / 동시성

| # | 룰 | 위반 시 등급 |
|---|-----|------------|
| B1 | `@Transactional`이 private 메서드에 달려있으면 안 됨 | Critical |
| B2 | `@Transactional` 범위가 외부 API 호출(HTTP, 메일 등)을 포함하면 안 됨 | Critical |
| B3 | 금액/수량/포인트 변경 → `@Lock(PESSIMISTIC_WRITE)` 또는 원자적 UPDATE 쿼리 | Critical |
| B4 | `@Version` 낙관적 락 사용 시 `OptimisticLockException` 재시도 로직 존재 | Warning |
| B5 | self-invocation(`this.method()`)으로 `@Transactional` 메서드 호출 금지 | Critical |

#### 체크리스트 C: 보안 / 인증

| # | 룰 | 위반 시 등급 |
|---|-----|------------|
| C1 | 데이터 변경 API(`POST/PUT/DELETE`)에 `@PreAuthorize` 존재 | Critical |
| C2 | Role 비교 시 하드코딩 문자열 대신 `RoleConstants` / `Role` enum 사용 | Warning |
| C3 | 민감 정보(비밀번호, 토큰)가 Response DTO에 포함되면 안 됨 | Critical |

#### 체크리스트 D: 데이터 정합성

| # | 룰 | 위반 시 등급 |
|---|-----|------------|
| D1 | 비즈니스 유니크 조건 → Entity에 `@UniqueConstraint` 선언 | Warning |
| D2 | `@UniqueConstraint` 위반 시 `DataIntegrityViolationException` catch 처리 | Warning |

#### 체크리스트 E: 코드 품질

| # | 룰 | 위반 시 등급 |
|---|-----|------------|
| E1 | `System.out.println` 잔존 | Warning |
| E2 | 미사용 import | Info |
| E3 | Response DTO가 record로 변환 가능 (필드 < 10개, setter 미사용) | Info |

### 3단계: 리뷰 결과 출력

각 Finding을 **[문제] → [원인] → [개선 코드]** 형식으로 출력한다:

```
## 코드 리뷰 결과

### 🔴 Critical

#### [A1] N+1 쿼리 발생 가능
- **파일**: `CareRequestService.java:45`
- **문제**: `@ManyToOne` 기본 fetch가 EAGER → 목록 조회 시 N+1
- **원인**: `CareRequest.provider` 필드에 `fetch = LAZY` 미선언
- **개선 코드**:
  ```java
  @ManyToOne(fetch = FetchType.LAZY)
  private User provider;
  ```

### 🟡 Warning
(동일 형식)

### 🟢 Info
(동일 형식)

### ✅ 잘된 점
- 설명
```

### 4단계: 요약 점수판

```
## 점수판

| 카테고리 | Critical | Warning | Info |
|---------|----------|---------|------|
| JPA/쿼리 | 1 | 0 | 0 |
| 트랜잭션 | 0 | 1 | 0 |
| 보안 | 0 | 0 | 0 |
| 정합성 | 0 | 1 | 0 |
| 코드품질 | 0 | 0 | 2 |
| **합계** | **1** | **2** | **2** |

→ Critical 0개 달성 시 "✅ 커밋 가능" / 1개 이상이면 "🔴 수정 필요"
```

### 5단계: 수정 제안

- Critical 항목은 구체적인 수정 코드를 함께 제시한다.
- "수정할까?" 확인 후 사용자가 동의하면 바로 적용한다.
- 수정 적용 후 → `/test` 실행을 제안한다.

## 워크플로우 연계

- 리뷰 완료 후 수정 필요 → `/refactor` 제안
- 수정 완료 후 → `/test` → `/commit` 순서 제안

## 제약

- 리뷰만 하고, 사용자 확인 없이 코드를 수정하지 않는다.
- 기존 아키텍처 패턴을 존중한다 (프로젝트 컨벤션 > 일반 베스트 프랙티스).
- 체크리스트에 없는 항목도 발견하면 Info로 보고한다.
