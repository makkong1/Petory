# Karpathy Guidelines (`/karpathy-guidelines`)

## 목적

루트 **`CLAUDE.md`**의 「기본 작업 규격」과 같은 철학을 **슬래시로 즉시 다시 읽을 때** 쓰는 리마인더 채널이다. 규격의 **근원은 항상 `CLAUDE.md`**이며, 본 파일은 미러에 가깝다. Cursor 쪽에는 동일 규격이 `.cursor/rules/karpathy-guidelines.mdc`(Project Rules)로 둔다.

## 에이전트 지시

1. 아래 원문 블록의 네 원칙(Think Before Coding → Simplicity → Surgical Changes → Goal-Driven Execution)을 **코딩·리뷰·리팩터링**에 적용한다.
2. 프로젝트 도메인 필수 규칙(JPA 인덱스·트랜잭션·보안 등)은 `CLAUDE.md` 본문과 `.claude/skills/review.md` 등과 함께 병행한다.
3. 상세 매핑·한글 종합: **`docs/AGENT_TOOLING.md`**

---

## 원문 — Karpathy Guidelines

Behavioral guidelines to reduce common LLM coding mistakes, derived from Andrej Karpathy's observations on LLM coding pitfalls.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

### 1. Think Before Coding

Don't assume. Don't hide confusion. Surface tradeoffs.

**Before implementing:**

- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### 2. Simplicity First

Minimum code that solves the problem. Nothing speculative.

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.
- Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical Changes

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

### 4. Goal-Driven Execution

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
