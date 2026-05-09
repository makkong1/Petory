---
name: db-review
description: Use when JPA entity, Repository, Service 코드에 DB 관련 변경이 있을 때 — N+1, 인덱스 누락, 트랜잭션 범위, 동시성 제어 문제를 점검
---

# DB Review Skill

이 프로젝트(Petory)의 DB 관련 변경사항을 점검하는 체크리스트.

## 1. N+1 문제

- [ ] LAZY 로딩된 연관 엔티티를 루프 안에서 접근하는가?
- [ ] `@OneToMany` / `@ManyToMany` 관계를 조회 후 순회하는가?
- [ ] 해결책: `JOIN FETCH`, `@EntityGraph`, 또는 배치 조회(`findAllById`) 사용

```java
// 나쁨: N+1
List<CareRequest> requests = careRequestRepository.findAll();
requests.forEach(r -> r.getApplications().size()); // 쿼리 N번 추가 발생

// 좋음: JOIN FETCH
@Query("SELECT r FROM CareRequest r JOIN FETCH r.applications WHERE r.status = :status")
List<CareRequest> findWithApplications(@Param("status") CareStatus status);
```

## 2. 인덱스 누락

- [ ] `WHERE` 절에 자주 쓰이는 컬럼에 인덱스가 있는가?
- [ ] `ORDER BY`, `GROUP BY` 컬럼에 인덱스가 있는가?
- [ ] 복합 조건은 복합 인덱스가 선행 컬럼 순서와 맞는가?

**이 프로젝트 주요 인덱스 체크 포인트:**
- `board`: `(category, is_deleted)`, `(user_id, is_deleted)`
- `care_request`: `(status, is_deleted)`, `(user_id)`, `(latitude, longitude)`
- `notification`: `(user_id, is_read)`, `(created_at)`
- `pet_coin_transaction`: `(user_id, created_at)`

엔티티에 인덱스 미선언 시:
```java
@Table(indexes = {
    @Index(name = "idx_board_category_deleted", columnList = "category, is_deleted")
})
```

## 3. 트랜잭션 범위

- [ ] `@Transactional`이 Service 계층에 있는가? (Controller에 있으면 안 됨)
- [ ] 읽기 전용 메서드에 `@Transactional(readOnly = true)` 적용됐는가?
- [ ] 트랜잭션 밖에서 LAZY 로딩을 시도하는가? (`LazyInitializationException` 위험)
- [ ] 너무 넓은 트랜잭션 범위로 커넥션을 오래 점유하는가?

```java
// 읽기는 readOnly
@Transactional(readOnly = true)
public List<BoardDTO> getBoards() { ... }

// 쓰기는 기본 트랜잭션
@Transactional
public BoardDTO createBoard(BoardDTO dto) { ... }
```

## 4. 동시성 제어

이 프로젝트 규칙:
- **펫코인 · 에스크로**: 비관적 락 (`findByIdForUpdate`)
- **경고 횟수 · 모임 인원**: DB 원자적 증가 (`@Query("UPDATE ... SET count = count + 1")`)
- **일반 상태 변경**: 낙관적 락 (`@Version`)

- [ ] 동시 요청이 가능한 자원에 락 전략이 있는가?
- [ ] 비관적 락 사용 시 데드락 위험 순서가 일관되는가?
- [ ] `@Version` 필드 없이 낙관적 락을 기대하는 코드가 있는가?

## 5. 페이징 / 대용량 조회

- [ ] 전체 목록 조회에 페이징이 없는가? (`findAll()` → `findAll(Pageable)`)
- [ ] `COUNT(*)` 쿼리가 매번 발생하는가? (Slice 사용 고려)
- [ ] 집계 쿼리를 실시간으로 돌리는가? (Daily Summary 배치 패턴 활용)

## 점수 기준

| 등급 | 기준 |
|------|------|
| ✅ 통과 | 위 체크리스트 이슈 없음 |
| ⚠️ 개선 권장 | readOnly 누락, 인덱스 미선언 등 성능 관련 |
| 🚨 수정 필요 | N+1, 동시성 버그, 트랜잭션 범위 오류 |
