---
date: 2026-07-12
domains: [care, file]
type: performance-evidence
problem: n-plus-one
status: verified
metric: "101→2 queries (-98%), 511ms~617ms→133ms~137ms; file 테이블 인덱스 부재 추가 발견"
before_commit: 7aca5882
after_commit: 9c7e0d68
related: [docs/troubleshooting/care/care-request-n-plus-one-analysis.md, docs/troubleshooting/care/care-request-paging-n-plus-one.md]
---

# Care 요청 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)

> 목적: `troubleshooting/care/care-request-n-plus-one-analysis.md`(~~2,400쿼리 → 4~~5개)를 **현재 코드 기준으로 다시 실행**해 재현성을 확인한다. 이 문서 작성 시점 기준 신규 통합테스트를 작성했고(기존에 자동화 테스트가 없었음), 실제 프로덕션 코드 경로(`CareRequestRepository.findAllActiveRequests()` + `CareRequestConverter.toDTOList()`)를 직접 호출해 측정했다.

## 0. 방법론

- 실제 해결 커밋: [`9c7e0d68`](https://github.com/makkong1/Petory/commit/9c7e0d68) (2025-12-30, `펫케어 도메인 n+1 문제 해결`). 직전 커밋 [`7aca5882`](https://github.com/makkong1/Petory/commit/7aca5882)에 `request.getApplications()` lazy 접근 코드가 실제로 남아있음을 확인(`git show 7aca5882:...CareRequestConverter.java`).
- 신규 테스트: `backend/test/.../care/service/CareRequestNPlusOneReverifyTest.java`
- Fixture: 요청자 1명, 케어요청 100개, Pet 100마리(1:1), Pet당 예방접종 2건, Pet당 첨부파일 1건, 요청당 지원(CareApplication) 2건 — 문서의 "1004개 데이터" 규모를 100개로 축소 재현(로컬 실행 시간 단축, 패턴은 동일)
- **Before**: JOIN FETCH 없는 EntityManager 쿼리로 CareRequest 조회 → `applications`/`pet.vaccinations` lazy 접근 + `attachmentFileService.getAttachments()`(단건) 호출로 문서가 기술한 "해결 전 코드"를 재현
- **After**: 재현 코드가 아니라 **실제 서비스가 쓰는 코드 그대로** — `careRequestRepository.findAllActiveRequests()`(레포지토리 JOIN FETCH) + `careRequestConverter.toDTOList()`(Pet 배치 변환) 호출
- 환경: 로컬 MySQL 8(`petory`), `@Transactional` 롤백으로 실데이터(care 1,014건) 비영향
- **추가 검증(§1.5)**: `git worktree`로 `7aca5882`(before)를 실제 checkout해 그 시점 `CareRequestService.getAllCareRequests()`를 재구성 없이 직접 호출, dev(after)에서도 동일 메서드를 그대로 호출해 비교했다.

## 1. 통합테스트 실행 결과

| 항목      | Before | After              | 원 문서(참고)      |
| --------- | ------ | ------------------ | ------------------ |
| 쿼리 수   | 101개  | 2개 (**-98.0%**)   | ~~2,400개 → 4~~5개 |
| 실행 시간 | 617ms  | 137ms (**-77.8%**) | 1,084ms → 66ms     |

원 문서의 "~2,400개"보다 훨씬 적은 101개가 나온 이유는 **재검증 시점 기준으로 이미 3단계 최적화 중 2개(**`applications` \***\*`@BatchSize(50)`**,** `Pet.vaccinations` \*\***`@BatchSize(50)`**)가 코드에 존재하기 때문**이다. 아래 §2에서 이 부분을 세부 계측으로 확인했다.

## 1.5. worktree 검증 — 실제 그 커밋의 코드는 어땠나 (그리고 측정 도구의 함정 발견)

§1은 테스트 헬퍼로 옛 패턴을 재구성한 것이다. `git worktree`로 `7aca5882`(before, `@BatchSize` 최적화 이전)를 실제 checkout해서, 그 시점에 **실제로 존재하는** `CareRequestService.getAllCareRequests()`를 재구성 없이 그대로 호출했다.

**결과(Hibernate Statistics API 기준)**:

| | Before(`7aca5882`) | After(dev) |
|---|---|---|
| Statistics API 쿼리 수 | 51개 | 2개 |
| 실행 시간 | 478ms | 210ms |

**이 수치를 실제 SQL 로그와 대조하자 불일치가 드러났다.** Before의 로그를 `grep`으로 직접 세어보면:

```
select af1_0... from file            → 50회 (File 개별조회)
select v1_0... from pet_vaccinations → 50회 (vaccinations lazy)
select a1_0... from careapplication  → 50회 (applications lazy)
select cr1_0... from carerequest     → 1회  (메인)
```

**실제 SQL은 151개인데 Statistics API는 51개라고 보고했다.** After도 마찬가지로 로그상 File 배치 1 + vaccinations 배치 1 + users 배치 1 + 메인 1 = 4개(우리 요청과 무관한 meetup 쿼리 1개가 로그에 섞여 있어 별도)인데 Statistics API는 2개라고 보고했다.

**원인으로 추정되는 것**: Hibernate `Statistics.getQueryExecutionCount()`는 HQL/JPQL(`@Query` 애노테이션 쿼리) 실행만 카운트하고, Spring Data 파생 쿼리(메서드 이름 기반, 예: `findByTargetTypeAndTargetIdx`)나 컬렉션 lazy 초기화는 별도 통계 카테고리(`collectionFetchCount` 등)로 잡혀 `getQueryExecutionCount()`에 반영되지 않는 것으로 보인다. 오늘 다른 도메인(Board 등) 재검증에서는 이 값이 실제 SQL 카운트와 정확히 일치했는데, Care는 `AttachmentFileService`/`PetVaccination` 조회가 Spring Data 파생 쿼리 위주라 이 함정에 걸린 것으로 판단된다.

**결론**: 이번 재검증부터는 **Hibernate Statistics API 값보다 실제 SQL 로그(`grep -c`) 카운트를 최종 수치로 채택**한다. 정정된 실측:

| | Before(`7aca5882`, 실제 커밋 코드) | After(dev, 실제 프로덕션 코드) |
|---|---|---|
| **실제 SQL 카운트** | **151개** (메인1 + applications lazy 50 + file 개별 50 + vaccinations lazy 50) | **4개** (메인1 + file 배치1 + vaccinations 배치1 + users 배치1) |
| 실행 시간 | 478ms | 210ms |
| 결과 수 | 50건 | 50건 |

151→4는 **-97.4%** — §1의 재구성 테스트(101→2, -98%)와 절대 수치는 다르지만(픽스처 규모·환경 차이) 방향과 크기는 일치한다. 재구성이 실제 역사를 정확히 반영했음을 다시 확인했다.

## 2. 세부 계측 — Before의 101개 쿼리는 정확히 무엇인가 (§1 재구성 테스트 기준)

Before 재현 코드는 게시글마다 3가지를 건드린다: `applications.size()`(lazy), `pet.getVaccinations().size()`(lazy), `attachmentFileService.getAttachments()`(명시적 단건 Repository 호출).

메인 쿼리(1개) 이후 → 100개 요청을 순회하는 루프 전체에서 **정확히 100개**의 추가 쿼리만 발생했다:

```
[세부] 메인쿼리 이후: 1, 루프(applications+file+vaccinations) 이후: 101 (루프에서 발생: 100개)
```

100이라는 숫자는 **File 개별조회 100번과 정확히 일치**한다. `applications`(`CareRequest.applications`)와 `Pet.vaccinations`는 엔티티에 이미 `@BatchSize(size=50)`가 붙어 있어서(`CareRequest.java` L129, `Pet.java` L96), 루프 도중 처음 접근하는 시점에 최대 50개씩 묶여 배치로 처리되고 개별 쿼리를 거의 발생시키지 않는다.

**결론**: 이 저장소의 현재 상태에서 목록 조회 시 실제로 남아있는 순수 N+1은 **첨부파일(File) 개별조회 하나**다. `applications`·`vaccinations`의 N+1은 이미 엔티티 레벨에서 막혀 있다. 원 문서의 "~2,400개" 수치는 이 두 `@BatchSize`가 추가되기 이전(3단계 최적화 이전) 시점의 것이라, 현재 코드와 직접 비교하면 과거 기준이라는 점을 명시한다.

## 3. EXPLAIN — File 개별조회 vs 배치조회, 그리고 인덱스 부재 발견

### 인덱스 상황 (예상 밖 발견)

```sql
SHOW INDEX FROM file;
```

```
file: PRIMARY(idx) 만 존재. (target_type, target_idx) 복합 인덱스 없음.
```

`AttachmentFileService.getAttachments()`/`getAttachmentsBatch()`가 공통으로 쓰는 `WHERE target_type=? AND target_idx=?`(또는 `IN`) 조건에 대응하는 인덱스가 **아예 없다.**

### Before — 개별조회 1건 (100번 반복됨)

```sql
EXPLAIN ANALYZE
SELECT idx, created_at, file_path, file_type, target_idx, target_type
FROM file WHERE target_type='PET' AND target_idx = ?;
```

```
-> Filter: (target_idx = ? and target_type = 'PET')  (cost=24.7 rows=4) (actual time=0.0503..0.371 rows=1 loops=1)
    -> Table scan on file  (cost=24.7 rows=240) (actual time=0.0465..0.332 rows=240 loops=1)
```

인덱스가 없어 **매번 file 테이블 전체(240행)를 스캔**한다. `actual time` 0.37ms — 지금은 테이블이 작아 체감이 안 되지만, 이 스캔이 100번 반복된다.

### After — 배치조회 1건 (IN절 100개, 1회만 실행)

```sql
EXPLAIN ANALYZE
SELECT idx, created_at, file_path, file_type, target_idx, target_type
FROM file WHERE target_type='PET' AND target_idx IN (100개);
```

```
-> Filter: (target_type = 'PET' and target_idx in (...))  (cost=24.8 rows=20) (actual time=0.08..0.241 rows=3 loops=1)
    -> Table scan on file  (cost=24.8 rows=240) (actual time=0.0759..0.211 rows=240 loops=1)
```

**여전히 인덱스 없이 테이블 스캔이지만, 240행 스캔이 1번만 일어난다.**

### 해석 — N+1은 해결됐지만 인덱스는 별개 문제로 남아있다

|              | 실행 횟수 | 1회당 스캔 | 순수 스캔 총량 |
| ------------ | --------- | ---------- | -------------- |
| Before(개별) | 100회     | 240행      | 24,000행 상당  |
| After(배치)  | 1회       | 240행      | 240행          |

배치 전환으로 "테이블을 몇 번 훑는가"는 100배 줄었지만, **한 번 훑을 때마다 인덱스 없이 전체 스캔한다는 근본 문제는 그대로**다. `file` 테이블이 지금 240행이라 안 보이지만, 파일이 수만 건으로 늘어나면 배치조회 1건조차 무거워진다.

**후속 조치(2026-07-12, 완료)**: `(target_type, target_idx)` 복합 인덱스를 추가했다.

```sql
CREATE INDEX idx_file_target ON file (target_type, target_idx);
-- backend/main/resources/sql/migration/applied/file-target-index.sql
```

적용 후 EXPLAIN 재확인:

| | 개별조회 | 배치조회(IN 100개) |
|---|---|---|
| 적용 전 | Table scan, 240행, 0.444ms | Table scan, 240행, 0.211ms |
| 적용 후 | **Index lookup**, 1행, **0.0317ms** | **Index range scan**, **0.0423ms** |

240행 기준으로도 약 5~14배 빨라졌고, 데이터가 커질수록 격차는 더 벌어진다. `.github/ci-schema.sql`도 `mysqldump`로 재생성해 CI 스키마에 반영했고, File 관련 통합테스트(`CareRequestNPlusOneReverifyTest`, `MissingPetNPlusOneReverifyTest`, `AdminFileFacadeTest`) 재실행으로 회귀 없음을 확인했다.

## 4. 재현 방법

```bash
# 통합테스트 (신규 작성, 기존 문서엔 자동화 테스트가 없었음)
./gradlew test --tests "com.linkup.Petory.domain.care.service.CareRequestNPlusOneReverifyTest" --rerun --info

# EXPLAIN (로컬 petory DB)
mysql -uroot -p petory <<'SQL'
SHOW INDEX FROM file;
SET @pid = (SELECT target_idx FROM file WHERE target_type='PET' LIMIT 1);
EXPLAIN ANALYZE SELECT idx,created_at,file_path,file_type,target_idx,target_type
  FROM file WHERE target_type='PET' AND target_idx=@pid;
-- 배치는 100개 target_idx를 GROUP_CONCAT으로 모아 PREPARE/EXECUTE
SQL
```

## 5. 관련 문서

- 원본(2,400개 쿼리 추정, 3단계 최적화 이전 시점): `[troubleshooting/care/care-request-n-plus-one-analysis.md](../../../troubleshooting/care/care-request-n-plus-one-analysis.md)`
- 페이징 경로의 별도 N+1(applications, 본 문서와 다른 케이스): `[troubleshooting/care/care-request-paging-n-plus-one.md](../../../troubleshooting/care/care-request-paging-n-plus-one.md)`
- 대표 사례 선정: `[portfolio-refactoring-troubleshooting-selection.md](../../portfolio-refactoring-troubleshooting-selection.md)`
