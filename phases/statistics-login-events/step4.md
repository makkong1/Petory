# Step 4 — docs 업데이트 (과거 통계 보정 불가 범위 명시)

## 목표
`docs/domains/statistics.md`에 login_events 도입 이후의 DAU 정확성 변화와, 과거 통계 보정 불가 범위를 명시한다.

## 수정 내용 (docs/domains/statistics.md)

### 추가할 섹션: "DAU 원천 전환 이력"

```markdown
## 10. DAU 원천 전환 이력 (2026-06-28)

### 배경
`Users.lastLoginAt`은 로그인마다 덮어쓰이므로, 하루에 2회 이상 로그인한 사용자가 배치 실행 전에
다시 로그인하면 전날 DAU에서 누락된다 (C0 버그).

### 수정 내용
- `login_events` 테이블 신설 (append-only, 로그인 1회 = 행 1개)
- `StatisticsAggregator.activeUsers` 집계: `Users.lastLoginAt` → `COUNT(DISTINCT login_events.user_id)`
- 인덱스: `(user_id, login_at)`, `(login_at)` 복합/단일 인덱스

### 보정 불가 범위
- **도입 이전 (~ 2026-06-27) 일별 통계의 `active_users`**: 보정 불가.
  - `Users.lastLoginAt`은 마지막 로그인 시각만 남기므로, 역산 불가.
  - 과거 `active_users` 값은 낮게 집계되었을 가능성 있음 (하루 2회+ 로그인 사용자 누락).
  - 주간/월간 `active_users`도 DAU 합산 기반이므로 동일하게 과소 집계됨.
- **보정 권장**: 도입 이전 `daily_statistics.active_users` 컬럼을 NULL로 표기하거나, 관리자 대시보드에서 "추정값" 레이블 표시 권장.

### WAU/MAU activeUsers 한계 (잔존)
현재 WAU `activeUsers` = 해당 주 DAU 합산, MAU `activeUsers` = 해당 월 DAU 합산.
진정한 주간/월간 DISTINCT는 `login_events`를 주·월 단위로 직접 GROUP BY해야 한다.
이 개선은 별도 태스크(`statistics-wau-mau-distinct`)로 분리한다.
```

## Acceptance Criteria
- `docs/domains/statistics.md` 섹션 10이 추가됨
- 보정 불가 날짜 범위(도입일 기준)가 명시됨
