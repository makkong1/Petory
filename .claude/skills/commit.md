# Commit + Push Skill

## 트리거

사용자가 커밋, 푸시, 변경사항 저장을 요청할 때 실행한다.

## 커밋 메시지 컨벤션

이 프로젝트의 커밋 메시지 형식:

```
<type>(<scope>): <한글 설명>
```

### type 자동 분류 규칙

| 변경 내용 | 자동 분류 type |
|-----------|--------------|
| 새 파일 + 새 API 엔드포인트 | `feat` |
| 기존 기능의 버그 수정 | `fix` |
| 기능 변경 없이 구조 개선 | `refactor` |
| `docs/` 하위 또는 `.md` 파일만 변경 | `docs` |
| 설정, 빌드, 의존성 변경 | `chore` |
| 쿼리 최적화, 인덱스 추가, N+1 해결 | `perf` |
| `*Test.java`, `*.test.js` 파일 변경 | `test` |

### scope 자동 분류 규칙

| 변경 파일 경로 | 자동 scope |
|--------------|-----------|
| `domain/board/` | `board` |
| `domain/care/` | `care` |
| `domain/chat/` | `chat` |
| `domain/location/` | `location` |
| `domain/meetup/` | `meetup` |
| `domain/notification/` | `notification` |
| `domain/payment/` | `payment` |
| `domain/report/` | `report` |
| `domain/user/` | `user` |
| `domain/statistics/` | `statistics` |
| `domain/file/` | `file` |
| `frontend/src/` | `frontend` 또는 구체적 컴포넌트명 |
| `global/`, `filter/`, `util/` | `security`, `config` 또는 생략 |
| 여러 도메인 혼합 | scope 생략 |

### 실제 예시 (이 프로젝트)
```
feat(meetup): 근처 모임 N+1 완화 및 목록/상태 개선
refactor(location): 주변 검색 위치 우선 및 DB 필터 정리
fix(ai-recommend): Ollama JSON 파싱 실패 수정 및 추천 로딩 UI 추가
docs: 도메인 및 아키텍쳐 파일 새작성
chore: 탭 통합 후 미사용 컴포넌트 삭제 및 아키텍처 문서 현행화
```

## 동작 절차

### 1단계: 변경 분석

```bash
git status
git diff --staged
git diff
```

- staged + unstaged 변경사항을 모두 파악한다.
- 비밀 파일(.env, credentials, application.properties 등)이 포함되면 **경고하고 제외**한다.

### 2단계: 파일 필터링 (안전장치)

변경된 파일 목록을 보여주고 커밋 범위를 확인한다:

```
## 변경 파일 목록

### ✅ 커밋 대상
- M  backend/.../CareRequestService.java
- M  backend/.../CareRequestController.java
- A  backend/.../CareRequestDTO.java

### ⚠️ 제외 (민감 파일)
- M  backend/main/resources/application.properties

### ❓ 확인 필요
- M  frontend/package-lock.json  (의존성 락 파일)

→ 이 파일들만 커밋할까? (Y/수정할 파일 번호)
```

**자동 제외 목록**: `.env`, `application.properties`, `application-*.properties`, `credentials*`, `*.key`, `*.pem`

### 3단계: 커밋 메시지 추천

- type과 scope를 자동 분류한 뒤, 메시지 2~3개를 추천한다.
- 여러 도메인이 섞여 있으면 **커밋 분리를 제안**한다.

출력 형식:
```
## 추천 커밋 메시지

자동 분류: type=`feat`, scope=`location`

1. `feat(location): AI 추천 결과를 지도·리스트 순위로 시각화`
2. `feat(location): 위치 서비스 AI 추천 기능 및 UI 추가`

→ 번호 선택 / 직접 수정 / "바로 푸시"(1번으로 커밋+푸시)
```

**여러 도메인 혼합 시:**
```
## ⚠️ 커밋 분리 제안

2개 도메인이 섞여 있어 분리를 권장한다:

### 커밋 1: backend 변경
- `refactor(care): 케어 서비스 예외 처리 통합`
- 대상: CareRequestService.java, CareException.java

### 커밋 2: frontend 변경
- `fix(frontend): 케어 요청 폼 유효성 검증 추가`
- 대상: CareRequestForm.js, careApi.js

→ 분리해서 커밋할까? (Y/한번에)
```

### 4단계: 커밋 실행

```bash
git add <확인된 파일>
git commit -m "<선택된 메시지>"
```

### 5단계: 푸시

- 커밋 성공 후 "푸시할까?" 확인한다.
- 사용자가 "바로 푸시" 또는 "푸시까지" 라고 했으면 확인 없이 바로 실행한다.

```bash
git push origin <current-branch>
```

### 6단계: 결과 요약

```
## 커밋 완료

- 📝 메시지: `feat(location): AI 추천 결과를 지도·리스트 순위로 시각화`
- 📁 파일: 3개 (변경 2, 신규 1)
- 🚀 푸시: origin/feature/ai-recommend ✅
```

## 빠른 모드

사용자가 "바로 커밋해", "커밋 푸시해" 등 빠른 실행을 요청하면:
1. 변경 분석 → 민감 파일 자동 제외 → type/scope 자동 분류 → 메시지 자동 선택 → 커밋 → 푸시
2. 결과 요약만 보여준다.

## 워크플로우 연계

- `/review` → `/test` 통과 후 → `/commit` 실행이 이상적
- 커밋 후 문서 변경이 필요하면 → `/docs` 제안

## 제약

- `--force`, `--amend`는 사용자가 명시적으로 요청할 때만 사용한다.
- main/master 브랜치에 force push는 경고 후 사용자 재확인을 받는다.
- 민감 파일은 자동 제외하되, 제외 사실을 반드시 알린다.
