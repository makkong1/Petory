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

### 1-1단계: 브랜치 확인 (작업은 feature 브랜치에서)

이 저장소의 흐름은 **`feature/*` → PR → `dev` → fast-forward → `main`** 이다.

- **`feature/*`** (또는 `fix/*`, `perf/*`, `refactor/*`) — 실제 작업. 여기서 커밋한다.
- **`dev`** — 개발 통합 브랜치. feature를 **PR로** 받는다. 리뷰·CI가 붙는 지점은 여기다(코드가 새로 생기는 곳).
- **`main`** — 확정된 상태를 가리키는 브랜치. `dev`에서 **fast-forward로만** 전진시킨다.
  ⚠️ **Petory는 아직 배포가 없다**(로컬 `bootRun` 뿐). `main`은 "배포된 것"이 아니라 "dev에서 확인이 끝난 지점"이다. 배포가 생기면 그때 의미를 올리면 된다.

> ⚠️ **`main`에 `dev`가 모르는 커밋이 생기면 두 브랜치가 어긋난다.** PR 머지커밋이 바로 그 커밋이다.
> 실제로 이 저장소는 `dev → main` PR을 37번 하는 동안 그만큼 쌓여 **dev가 41커밋 뒤처진 적이 있다**(2026-09-14 `--ff-only`로 정리).
> 그래서 **`dev → main`에는 PR을 쓰지 않는다.** 아래 「dev → main 반영」 절 참고.
>
> 🔴 **2026-09-19 재발.** PR #275·#277 로 다시 2커밋 어긋났고, 그 자리에서 되받아 정렬했다.
> 재발 자체보다 **어떻게 재발했는지가 중요하다** — 에이전트가 최근 이력(#271~#275 가 전부 PR)을 보고
> *"실제 관행이 PR이니 이 문서가 낡았다"* 고 판단해 **규칙을 문서에서 지우려 했다.** 거꾸로다.
> **이력은 규칙이 지켜졌는지를 보여줄 뿐, 규칙이 무엇인지는 이 문서가 정한다.**
> 규칙과 이력이 어긋나 보이면 고칠 대상은 문서가 아니라 브랜치다.
>
> 그리고 `--ff-only` 가 이미 불가능한 상태(= 누가 PR 로 머지해 둔 뒤)라면, 문서를 의심하지 말고
> 아래 「브랜치 보호」 각주의 **되받기**를 그대로 실행한다. 그게 그 상황의 처방이다.
>
> ✅ **2026-09-19 사용자 재확인: `--ff-only` 유지.** PR 방식으로 바꾸자는 선택지를 같이 놓고
> 물어본 결과다. **다시 꺼내지 말 것** — 이력에 PR 머지가 보여도 그건 규칙이 안 지켜진 자국이다.

```bash
git branch --show-current
```

| 현재 브랜치 | 동작 |
|---|---|
| `feature/*` 등 작업 브랜치 | 통과 — 바로 다음 단계 |
| `dev` | **작업 브랜치를 딸지 묻는다.** 코드 변경이면 기본값은 브랜치 생성(`git checkout -b <type>/<주제>`). 문서·설정 등 사소한 변경만 dev 직접 커밋을 허용한다. |
| `main`/`master` | **커밋하지 않는다.** 알리고 작업 브랜치로 전환한다. `main`은 확정 지점을 가리킬 뿐 직접 커밋하는 곳이 아니다. 사용자가 명시적으로 지시한 hotfix만 예외. |

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
- **같은 기능/태스크의 변경은 도메인이 달라도 하나의 커밋으로 묶는다.**
- 커밋 분리는 아래 기준에서만 제안한다.

출력 형식:
```
## 추천 커밋 메시지

자동 분류: type=`feat`, scope=`location`

1. `feat(location): AI 추천 결과를 지도·리스트 순위로 시각화`
2. `feat(location): 위치 서비스 AI 추천 기능 및 UI 추가`

→ 번호 선택 / 직접 수정 / "바로 푸시"(1번으로 커밋+푸시)
```

**커밋 분리 기준 (이 경우에만 제안):**
- 기능적으로 완전히 독립된 작업 2개가 동시에 변경된 경우
- `feat`와 `fix`가 섞인 경우 (기능 추가 + 버그 수정이 동시에)
- 민감 파일 제외 처리가 필요한 경우

**분리하지 않는 경우 (한 커밋으로 묶기):**
- 같은 플랜/태스크에서 나온 변경 (entity + repository + service + controller가 한 기능)
- backend + frontend가 같은 기능을 구현한 경우
- 여러 도메인 파일이지만 하나의 기능을 위한 변경인 경우

```
## 추천 커밋 메시지 (여러 도메인 혼합)

변경 파일이 care / report / user 3개 도메인에 걸쳐 있지만
모두 "통계 집계 쿼리 추가"라는 하나의 목적이므로 함께 커밋한다.

1. `feat(statistics): 취소 케어·처리 신고·신규 제공자 집계 쿼리 추가`
2. `feat: 통계 배치 집계용 Repository 쿼리 추가 (care/report/user)`

→ 번호 선택 / 직접 수정
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

### 5-1단계: main 푸시 시 CI/CD 자동 확인

**푸시 대상이 `main`이면**, push 직후 GitHub Actions가 실제로 트리거됐는지 확인하고 완료까지 지켜본다 (파일이 존재한다고 실행됐다고 가정하지 않는다):

```bash
gh run list --workflow=<트리거되는 워크플로우 파일명>.yml --limit 3
gh run watch <run-id> --exit-status
```

- 트리거 안 됐으면(`gh run list`에 방금 push 시각의 run이 없으면) 워크플로우의 `on.push.branches`에 `main`이 포함되어 있는지, 파일이 실제로 main 브랜치에 존재하는지 확인한다.
- `gh run watch`가 실패로 끝나면 로그(`gh run view <run-id> --log-failed`)를 확인해 원인을 요약해서 알린다. 조용히 넘어가지 않는다.
- dev→main 병합 직후처럼 여러 워크플로우가 동시에 트리거될 수 있으면 각각 확인한다.

### 6단계: 결과 요약

```
## 커밋 완료

- 📝 메시지: `feat(location): AI 추천 결과를 지도·리스트 순위로 시각화`
- 📁 파일: 3개 (변경 2, 신규 1)
- 🚀 푸시: origin/feature/ai-recommend ✅
```

**`main` 푸시였다면** CI/CD 실행 결과도 함께 보고한다:

```
- 🔄 CD (Build & Push Docker Images): ✅ 성공 (run 28734294862)
```

## dev → main 반영 (fast-forward)

`dev`에서 확인이 끝난 지점을 `main`에 반영할 때는 **PR을 만들지 않는다.** 로컬 `main` 포인터를 fast-forward로 전진시키고 원격에 올릴 뿐이다 — 빌드도 서버도 관여하지 않는다.

```bash
git checkout main
git merge --ff-only dev     # 머지커밋을 만들지 않는다. 어긋나 있으면 거부하고 멈춘다
git push origin main
git checkout dev
```

- **`--ff-only`가 안전장치다.** `main`에 `dev`가 모르는 커밋이 있으면 조용히 머지하지 않고 **실패한다.** 그때는 덮지 말고 왜 갈라졌는지부터 확인한다(`git log --oneline dev..origin/main`).
- push 후에는 **5-1단계(CI/CD 트리거 확인)**를 그대로 수행한다.
- 구분점을 남기고 싶으면 PR 번호 대신 **태그**를 쓴다(선택):

```bash
git tag -a v0.3.0 -m "<무엇이 들어갔는지>"
git push origin v0.3.0
```

> ⚠️ 나중에 `main`에 브랜치 보호를 걸면 직접 push가 막혀 이 방식이 깨진다. 그땐 릴리스 PR로 바꾸되, **머지 직후 dev를 되받는 것**을 규칙으로 붙인다:
> `git checkout dev && git pull --ff-only origin main && git push origin dev`

## 빠른 모드

사용자가 "바로 커밋해", "커밋 푸시해" 등 빠른 실행을 요청하면:
1. 변경 분석 → 민감 파일 자동 제외 → type/scope 자동 분류 → 메시지 자동 선택 → 커밋 → 푸시
2. 결과 요약만 보여준다.

## 워크플로우 연계

- `/review` → `/test` 통과 후 → `/commit` 실행이 이상적
- 커밋 후 문서 변경이 필요하면 → `/docs` 제안

## 제약

- **`dev → main`은 항상 `--ff-only`.** `main`에 머지커밋을 만들지 않는다.
- **`feature → dev`는 PR로 받는다.** 로컬 머지로 dev에 직접 밀지 않는다(리뷰·CI 지점을 잃는다).
- ⚠️ `docs/` 하위 `.md`를 커밋하면 pre-commit 훅이 `docs/INDEX.md`를 재생성한다(`scripts/docs_index.py`, **PyYAML 필요**). PyYAML이 없으면 커밋이 막힌다 — `--no-verify`로 넘기기 전에 **해당 문서가 frontmatter를 갖는지 / `docs/INDEX.md`에 등재돼 있는지** 확인하고, 인덱스에 영향이 없을 때만 넘긴다.
- `--force`, `--amend`는 사용자가 명시적으로 요청할 때만 사용한다.
- main/master 브랜치에 force push는 경고 후 사용자 재확인을 받는다.
- 민감 파일은 자동 제외하되, 제외 사실을 반드시 알린다.
