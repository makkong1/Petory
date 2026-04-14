# Documentation Skill

## 트리거

사용자가 문서화, 문서 동기화, 문서 업데이트를 요청할 때 실행한다.

## 이 프로젝트의 문서 구조

```
docs/
├── domains/           # 도메인별 현행 스펙 (board.md, care.md, location.md 등)
├── architecture/      # 아키텍처 문서 (시스템 설계, 시퀀스 다이어그램)
├── refactoring/       # 리팩토링 기록 (before/after, 성능 비교)
├── troubleshooting/   # 트러블슈팅 기록 (문제→원인→해결)
├── performance/       # 성능 측정 결과
├── deployment/        # 배포 가이드
├── interview/         # 면접 준비
├── 자료구조/알고리즘/   # 도메인별 알고리즘 설명
└── analysis/          # 분석 문서
```

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
```markdown
# {제목}

## 문제
## 원인
## 해결 (Before → After)
## 성능 비교
| 항목 | Before | After | 개선율 |
|------|--------|-------|-------|
## 참고
```

#### 트러블슈팅 문서 (`docs/troubleshooting/`)
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

## 워크플로우 연계

- `/refactor` 완료 후 → `/docs` 자동 제안 (리팩토링 기록)
- `/fix` 완료 후 → `/docs` 자동 제안 (트러블슈팅 기록)
- `/commit` 시 문서 변경 필요하면 → 커밋 전 `/docs` 제안

## 제약

- 추측으로 문서를 작성하지 않는다. 코드에서 확인한 사실만 기록한다.
- 기존 문서 형식을 최대한 유지한다.
- 면접 문서(`docs/interview/`)는 사용자가 명시적으로 요청할 때만 수정한다.
