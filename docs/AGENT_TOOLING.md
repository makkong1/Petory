# Karpathy Guidelines 이식 — 한 문서로 레거시 프로젝트에 그대로 적용

## 다른 프로젝트가 “레거시”인 이유

**지금 Cursor Project Rules (`/.cursor/rules` 등)·Claude Code 슬래시·`CLAUDE.md` 안에 아래「원문」에 해당하는 규칙이 없는 저장소**가 그것이다.  
우리가 이 **harness_framework** 레포에서 한 **궁극적 목적**:

> Karpathy 형의 **기본 작업 스타일(코딩 품격)** 을 에이전트의 **항상 적용 규격**으로 둔다.  
> **최상위 인터페이스는 `CLAUDE.md`** — 사용자 요청 들어오면 에이전트는 **먼저** 거기부터 읽고 그 스타일로 일한다.

그 다음 같은 규격이 **복제·영어 원문 참조·리마인더** 차원으로 **② Cursor `.cursor/rules/karpathy-guidelines.mdc`**, **③ `/karpathy-guidelines`** 에도 있다. 순서 의미상 **②③은 부가 채널**이며, “별도 채널만 쓸 때만 적용”이 아니라 **항상 깔린 스타일의 미러**.

워크플로(`workflow`, `/review`, `/test`…)는 **더 아래 레이어**(습관·체크리스트용 슬래시). **코어 정신은 여전히 동일 규격 = Karpathy 체계.**

---

## 이 레포에 적용된 매핑

| 우선순위 | 들어간 파일 | 역할 |
|----------|-------------|------|
| **1 (최상위)** | **`CLAUDE.md`** 상단 (기본 작업 스타일 문구 + 「에이전트 행동 가이드」한글 종합 + CRITICAL·명령어) | **항상** 에이전트가 사용자 요청을 처리할 때의 규격 루트 |
| **2** | `.cursor/rules/karpathy-guidelines.mdc` (`alwaysApply: true`) | 같은 규격의 **영어 원문·Cursor Project Rules 미러**(CLAUDE와 충돌 시 CLAUDE 우선) |
| **3** | `.claude/commands/karpathy-guidelines.md` → **`/karpathy-guidelines`** | 같은 규격을 **슬래시로 다시 깔 때** 리마인더 채널 |
| 선택 | `.claude/commands/review.md`, `workflow.md` | 규격을 전제로 한 체크·순서 |

**다른 레포:** 최소 **① `CLAUDE.md`부터** 채워 넣어야 “기본 작업 스타일” 완결. 그다음 필요 시 ②③ 복제.

**Cursor 쪽:** 에이전트가 루트 `CLAUDE.md`를 **자동으로 반드시 로드**한다는 보장은 제품·설정마다 다르다. 동일 “최상위 인터페이스”를 쓰려면 팀에서 **`CLAUDE.md`를 워크스페이스 규칙/지시에 포함**하거나, 규칙이 `CLAUDE.md`를 명시적으로 읽게 두는 것을 권장한다.

---

## 2. 복제의 기준 원문 — Karpathy Guidelines (메타 + 전문)

아래 블록이 **다른 프로젝트에 없을 때 빠진 것**이다. 에이전트에게 “이 §2를 Cursor rules(`.mdc`)·슬래시·`CLAUDE.md`에 반영해”라고 해도 된다.

```
name: karpathy-guidelines

description: Behavioral guidelines to reduce common LLM coding mistakes. Use when
writing, reviewing, or refactoring code to avoid overcomplication, make surgical
changes, surface assumptions, and define verifiable success criteria.

license: MIT
```

### Karpathy Guidelines

Behavioral guidelines to reduce common LLM coding mistakes, derived from Andrej Karpathy's observations on LLM coding pitfalls.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

#### 1. Think Before Coding

Don't assume. Don't hide confusion. Surface tradeoffs.

**Before implementing:**

- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

#### 2. Simplicity First

Minimum code that solves the problem. Nothing speculative.

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.
- Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

#### 3. Surgical Changes

Touch only what you must. Clean up only your own mess.

**When editing existing code:**

- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it — don't delete it.

**When your changes create orphans:**

- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

**The test:** Every changed line should trace directly to the user's request.

#### 4. Goal-Driven Execution

Define success criteria. Loop until verified.

**Transform tasks into verifiable goals:**

- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

**For multi-step tasks, state a brief plan:**

1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

---

## 에이전트에게 한 줄로 시킬 때 (레거시 레포)

> **`CLAUDE.md`를 최상위 기본 작업 규격으로** 삼아: 파일 맨위에 스타일이 항상 적용된다는 문단 + 「에이전트 행동 가이드」한글 종합(**`docs/AGENT_TOOLING.md` §2와 동등 철학**). 필요하면 **같은 §2 원문**(영어)으로 `.cursor/rules/karpathy-guidelines.mdc` 와 `.claude/commands/karpathy-guidelines.md` 를 추가해 **미러·슬래시 리마인더**만 둔다.  
> 워크플로 슬래시 묶음은 선택 — 이 harness의 `.claude/commands/` 폴더 통째 복사 후 `review`·`test`·`docs-sync`만 경로·커맨드 수정.

---

## 부가: 이 레포에 같이 둔 워크플로 파일 (`/review` 등)

Karpathy 규격 **위에 쌓은 절차 습관**이다. **기본 스타일 정의는 여전히 `CLAUDE.md`** — 이 슬래시들은 “그 스타일을 지키며 어떤 순서로 할지”만 잡는다.

---

## 복사 후 최소 검증

- `CLAUDE.md`의 **실제 빌드/테스트 명령** 한 번 성공.
- `/karpathy-guidelines` 파일이 있으면 Claude Code에서 해당 슬래시로 열리는지 확인.
- Cursor는 **Settings → Rules**에서 해당 워크스페이스의 Project rules에 `.cursor/rules/karpathy-guidelines.mdc`가 포함되는지 확인(`alwaysApply: true`면 대화마다 우선 포함).

`.claude/settings.json`의 훅은 **그대로 복사 금지**(npm 가정 등). 대상 스택에 맞추거나 비운다.
