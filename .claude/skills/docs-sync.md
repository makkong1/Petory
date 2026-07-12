# Documentation Skill

## 트리거

사용자가 문서화, 문서 동기화, 문서 업데이트를 요청할 때 실행한다.

## 이 프로젝트의 문서 구조

```
docs/
├── INDEX.md            # 자동 생성 인덱스 — 직접 수정 금지, scripts/docs_index.py로 재생성
├── domains/            # 도메인별 현행 스펙 (board.md, care.md, location.md 등)
├── architecture/       # 아키텍처 문서 (시스템 설계, 시퀀스 다이어그램)
├── refactoring/        # 리팩토링 기록 (before/after, 성능 비교)
├── troubleshooting/    # 트러블슈팅 기록 (문제→원인→해결)
├── performance/        # 성능 측정 결과
├── deployment/         # 배포 가이드
├── interview/          # 면접 준비
├── 자료구조/알고리즘/    # 도메인별 알고리즘 설명
└── analysis/           # 분석 문서
```

## 문서 인덱스 (frontmatter)

`docs/refactoring/`, `docs/troubleshooting/`, `docs/performance/`에 새 문서를 쓰거나 성능 수치·before/after가 있는 기존 문서를 수정할 때는, 도메인·날짜·문제유형이 서로 다른 축이라 파일 경로 하나로는 찾기 어렵다. 문서 맨 위에 아래 frontmatter를 붙이면 `docs/INDEX.md`가 날짜순·도메인별·문제유형별 3가지 관점으로 자동 인덱싱한다.

```yaml
---
date: YYYY-MM-DD
domains: [board]          # 여러 도메인 걸치면 배열: [board, care]
type: performance-evidence  # performance-evidence | n-plus-one | concurrency | security 등
problem: n-plus-one         # n-plus-one | overfetching | query-optimization | row-by-row-update 등
status: verified             # verified(실측 완료) | estimated(추정치) | superseded(구버전)
metric: "301→3 queries (-99%), 561ms→55ms"
before_commit: 19b7c120     # before 코드가 실제 존재하던 마지막 커밋(그 커밋의 부모 상태). 못 찾았으면 reconstructed
after_commit: 19b7c120      # 해결이 반영된 커밋
related: [docs/troubleshooting/board/performance-optimization.md]
---
```

frontmatter는 선택이다 — 없으면 인덱스에서 빠질 뿐 다른 기능에 영향 없다. 문서를 쓰거나 고친 뒤에는 `python3 scripts/docs_index.py`를 실행해 인덱스를 최신화한다.

### before/after 코드는 추측·재구성 전에 먼저 git 이력에서 찾는다

before/after 성능·동시성 문서를 쓸 때 가장 흔한 실수는, 실제 "고치기 전 코드"를 확인하지 않고 테스트 헬퍼 메서드 안에서 "아마 이랬을 것"으로 재구성하는 것이다. 이러면 실제 역사적 버그와 미묘하게 다른 코드를 비교하게 될 위험이 있다. 순서를 지킨다:

1. **먼저 커밋을 찾는다**: `git log --oneline --all -- <관련 파일 경로>` 로 문제를 고친 커밋을 찾는다. 커밋 메시지에 "n+1", "동시성", "race condition", "배치" 등이 들어간 것을 우선 본다.
2. **커밋 날짜와 원 문서 작성일을 대조한다**: 원 트러블슈팅/리팩토링 문서의 최초 작성일(`git log --diff-filter=A --format=%ad --date=short -- <문서경로> | tail -1`)과 후보 커밋의 날짜가 근접하면 그 커밋이 맞다.
3. **실제 이전 코드를 확인한다**: `git show <commit>^:<파일경로>` 로 그 커밋 직전의 실제 코드를 읽는다. 이게 "재구성"이 아니라 "실제 before"다.
4. **재구성이 불가피하면 정직하게 표시한다**: 히스토리에서 정확한 커밋을 못 찾았거나(리팩토링이 누적돼 원형이 안 남음), 처음부터 자동화 테스트가 없어 재현 코드를 새로 짜야 했다면, `before_commit: reconstructed`로 표시하고 문서 본문에 "실제 커밋이 아니라 원 문서 설명을 재현한 코드"라고 명시한다.

이 검증 없이 만든 before/after 비교는 "패턴은 맞지만 정확한 역사적 재현은 아닐 수 있다"는 한계를 반드시 문서에 남긴다.

## 동작 절차

### 1단계: 변경 영향 분석 (자동 트리거)

변경된 코드 파일을 기반으로 **어떤 문서를 수정해야 하는지 자동으로 판단**한다:

#### 영향 매핑 테이블

| 변경된 파일 유형 | 영향 받는 문서 | 필수 업데이트 |
|---------------|-------------|-------------|
| `*Controller.java` | `docs/domains/<domain>.md` → API 섹션 | Method, URL, 요청/응답 형식 |
| `*Entity.java` | `docs/domains/<domain>.md` → 엔티티 섹션 | 필드, 관계, 제약조건 |
| `*Service.java` | `docs/domains/<domain>.md` → 비즈니스 로직 | 핵심 흐름, 분기 조건 |
| `*Repository.java` | `docs/domains/<domain>.md` → 쿼리 | 커스텀 쿼리 설명 |
| `SecurityConfig.java` | `docs/architecture/` | 인증/인가 흐름 |
| `*DTO.java` / `*Request.java` / `*Response.java` | `docs/domains/<domain>.md` → API 섹션 | Request/Response 필드 변경 |
| `application.properties` 관련 | `docs/deployment/` | 설정 변경사항 |
| `build.gradle` | `docs/deployment/`, `README.md` | 의존성 변경 |

### 2단계: 문서 수정 범위 출력

```
## 문서 업데이트 필요

코드 변경 기반으로 아래 문서가 영향받는다:

### 📋 필수 업데이트
1. `docs/domains/care.md` → API 섹션
   - 변경: POST /api/care/requests 응답 형식 변경
   - 변경: CareRequestDTO에 필드 2개 추가

2. `docs/domains/care.md` → 엔티티 섹션
   - 변경: CareRequest 엔티티에 UniqueConstraint 추가

### 📝 권장 업데이트
3. `docs/architecture/펫 케어 & 매칭 아키텍처.md`
   - 시퀀스 흐름에 검증 단계 추가됨

→ 어디까지 업데이트할까? (전부 / 번호 선택)
```

### 3단계: 코드 기반 사실 확인

문서를 작성/수정하기 전에 반드시 실제 코드를 읽어서 사실을 확인한다:
- Entity 필드, 관계, 제약조건
- Controller 엔드포인트, 요청/응답 형식
- Service 비즈니스 로직 흐름
- Repository 쿼리 메서드

**코드와 문서가 다르면 코드가 진실이다.**

### 4단계: 문서 작성/수정

#### 도메인 문서 (`docs/domains/`)
```markdown
# {Domain} 도메인

## 엔티티
- 필드 목록, 관계, 제약조건

## API
| Method | URL | 설명 | 인증 | Request | Response |
|--------|-----|------|------|---------|----------|

## 비즈니스 로직
- 핵심 흐름 설명

## 관련 문서
- 아키텍처: docs/architecture/...
- 리팩토링: docs/refactoring/...
```

#### 리팩토링 문서 (`docs/refactoring/`)

파일명에 날짜를 포함한다: `{제목}-YYYY-MM-DD.md` (오늘 날짜 기준). before/after 수치가 있으면 위 frontmatter를 붙인다.

```markdown
---
date: YYYY-MM-DD
domains: [...]
type: performance-evidence
problem: n-plus-one
status: verified
metric: "..."
---

# {제목} — YYYY-MM-DD

## 문제
## 원인
## 해결 (Before → After)
## 성능 비교
| 항목 | Before | After | 개선율 |
|------|--------|-------|-------|
## 참고
```

#### 트러블슈팅 문서 (`docs/troubleshooting/`)

정량 수치(쿼리 수·응답시간 등)가 있으면 위 frontmatter를 붙인다.

```markdown
# {제목}

## 증상
## 원인 분석
## 해결 방법
## 재발 방지
```

### 5단계: 관련 문서 참조 업데이트

- 새 문서를 만들면 관련 도메인 문서에서 링크를 추가한다.
- `.cursorrules`나 `CLAUDE.md`에 참고 문서로 등록이 필요하면 제안한다.

### 6단계: 변경 요약

```
## 문서 업데이트 완료

| 문서 | 변경 내용 |
|------|----------|
| `docs/domains/care.md` | API 3건 추가, 엔티티 필드 2건 업데이트 |
| `docs/architecture/...` | 시퀀스 다이어그램 단계 1건 추가 |
```

### 7단계: 인덱스 재생성 (frontmatter를 붙인 경우만)

리팩토링/트러블슈팅/성능 문서에 frontmatter를 붙였거나 수정했으면 `python3 scripts/docs_index.py`를 실행해 `docs/INDEX.md`를 갱신한다.

## 워크플로우 연계

- `/refactor` 완료 후 → `/docs` 자동 제안 (리팩토링 기록)
- `/fix` 완료 후 → `/docs` 자동 제안 (트러블슈팅 기록)
- `/commit` 시 문서 변경 필요하면 → 커밋 전 `/docs` 제안

## 제약

- 추측으로 문서를 작성하지 않는다. 코드에서 확인한 사실만 기록한다.
- 기존 문서 형식을 최대한 유지한다.
- 면접 문서(`docs/interview/`)는 사용자가 명시적으로 요청할 때만 수정한다.
