# /harness

이 프로젝트는 Harness 프레임워크를 사용한다. 아래 워크플로우에 따라 작업을 진행하라.

## 워크플로우

### A. 탐색 (Exploration)
`agent-docs/` 하위 문서(PRD, ARCHITECTURE, ADR 등)를 읽고 프로젝트의 기획·아키텍처·설계 의도를 파악한다.

### B. 논의 (Discussion)
구현을 위해 구체화하거나 기술적으로 결정해야 할 사항이 있으면 사용자에게 제시하고 논의한다.

### C. Step 설계 (Step Design)
사용자가 구현 계획 작성을 지시하면 여러 step으로 나뉜 초안을 작성해 피드백을 요청한다. (Scope 최소화, 자기완결성 원칙 준수)

### D. 파일 생성 (File Creation)
사용자가 승인하면 아래 파일들을 생성한다.

- `phases/{task-name}/index.json`: Task 상세 정보 및 Step 목록
- `phases/{task-name}/step{N}.md`: 각 Step별 상세 구현 지시서

### E. 실행 (Execution)
```bash
python3 scripts/execute.py {task-name}
```
스크립트 마지막에 **대화형**으로 묻는다: (1) `feat-{task-name}` 를 origin 에 푸시할지, (2) 로컬 `dev` 에 머지한 뒤 `origin dev` 로 푸시할지. 파이프·CI처럼 stdin 이 tty 가 아니면 두 단계 모두 건너뛴다.

## Step 설계 원칙
1. **Scope 최소화**: 하나의 step에서 하나의 레이어 또는 모듈만 다룬다.
2. **자기완결성**: 각 step 파일은 독립적으로 실행 가능하다. 필요한 모든 맥락을 파일 안에 적는다.
3. **가드레일**: `agent-docs/` 및 `CLAUDE.md` 규칙을 엄격히 준수한다.
4. **AC (Acceptance Criteria)**: 실행 가능한 검증 커맨드(예: `./gradlew test`)를 포함한다.

## 상태 업데이트 규칙
- Step 완료 시: `status: "completed"`, `summary: "산출물 한 줄 요약"` 기록.
- 실패 시: `status: "error"`, `error_message: "내용"` 기록.
- 차단 시: `status: "blocked"`, `blocked_reason: "사유"` 기록.
