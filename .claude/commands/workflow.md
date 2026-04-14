# 개발 워크플로우 순서 (Pipeline)

## 이 파일의 역할

Cursor / Claude Code에서 **스킬을 어떤 순서로 쓰면 되는지** 한곳에 고정한다.  
개별 스킬(`review.md`, `refactor.md` 등)의 **「워크플로우 연계」**는 이 순서를 따른다.

---

## 권장 순서 (기본 파이프라인)

```
코드 수정
   │
   ▼
① /review     … 룰 기반 점검, 점수판, Critical/Warning 분류
   │
   ├─ 버그·장애·재현된 실패     → ②-A /fix   (Hotfix + Proper Fix)
   │
   └─ 설계·성능·가독성 개선     → ②-B /refactor   (문서·계획 기반 수정)
           │
           ▼
③ /test       … 변경분 기준 단위·통합·API 테스트 (정상/예외/경계 + 동시성·트랜잭션 해당 시)
   │
   ▼
④ /commit     … 스테이징·민감파일 제외·메시지·(선택) 푸시
   │
   ▼
⑤ /docs       … docs-sync: 영향 문서만 현행화 (코드 변경 후)
```

**한 줄 요약**: `review → (fix | refactor) → test → commit → docs`

---

## 분기 규칙

| 상황 | 다음 스킬 |
|------|-----------|
| `/review`에서 **Critical ≥ 1** (또는 보안·데이터 정합성 이슈) | `/refactor` 또는 이슈 성격이 **버그**면 `/fix` |
| `/review`에서 **Critical 0**, Warning만 | 사용자가 바로 커밋해도 되면 `/test` 생략 가능 — 다만 **동시성·결제·채팅 상태** 등은 `/test` 권장 |
| `/fix` 또는 `/refactor` 적용 직후 | **반드시** `/test` |
| `/test` 전부 통과 후 | `/commit` |
| `/commit`으로 코드가 원격에 반영된 뒤 | `/docs` (또는 같은 PR 안에서 문서까지 포함 커밋) |

---

## 스킬 ↔ 파일 매핑

| 순서 | 호출 | 정의 파일 |
|-----|------|-----------|
| ① | `/review` | `review.md` |
| ②-A | `/fix` | `fix.md` |
| ②-B | `/refactor` | `refactor.md` |
| ③ | `/test` | `test.md` |
| ④ | `/commit` | `commit.md` |
| ⑤ | `/docs` | `docs-sync.md` |

Claude Code 터미널에서는 동일 내용이 `.claude/commands/`에 있으면 `/` 명령으로 호출할 수 있다.

---

## 주의

- **`--force` / `--amend`**: `commit.md` 규칙대로 사용자가 명시할 때만.
- **민감 파일**: `.env`, `application.properties` 등은 `/commit` 단계에서 자동 제외·경고.
- 이 순서는 **권장 기본값**이며, 긴급 핫픽스만 먼저 나가야 하면 `fix → commit` 후 나머지를 이어 붙일 수 있다 — 그 경우에도 가능한 한 `/test`로 회귀를 잡는 것을 권장한다.
