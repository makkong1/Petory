---
date: 2026-07-13
domains: [board, meetup, payment, location]
type: data-integrity-evidence
problem: denormalized-counter-drift
status: verified
metric: "반정규화 카운터 7종 실측 — 코드는 정합, 데이터(더미)가 오염. like_count 168/10264, comment_count 548/10264, current_participants 2791/3736 불일치"
related: [docs/analysis/entity-schema/02-normalization-analysis.md]
---

# 반정규화 필드 정합성 실측 (2026-07-13)

`02-normalization-analysis.md` 는 "집계/캐시 필드의 동기화 로직이 비즈니스 레이어에 명확히 구현되어 있는지 확인 필요"로 끝났다.
이 문서는 그 확인을 **실제 DB에 쿼리를 던져서** 수행한 결과다.

측정 대상: 로컬 개발 DB (MySQL 8.4, board 10,264행 / meetup 3,736행 / users 7,163행)

---

## 1. 측정 결과

| 반정규화 필드 | 원본 집계 | 일치 | 불일치 | 불일치 양상 |
|---|---|---|---|---|
| `board.like_count` | `board_reaction` (LIKE) | 10,096 | **168** | 전부 정확히 **+1** |
| `board.dislike_count` | `board_reaction` (DISLIKE) | 10,264 | 0 | — |
| `board.comment_count` | `comment` (미삭제) | 9,716 | **548** | **+1** 536건, **+2** 12건 |
| `board.view_count` | `board_view_log` | 12 | **10,252** | 총합 5,154,619 vs 로그 **17행** |
| `meetup.current_participants` | `meetupparticipants` | 945 | **2,791** | 차이 0~7+ **무작위 분산** |
| `users.pet_coin_balance` | `pet_coin_transaction.balance_after` | 2 | 0 | 거래 이력 유저 2명뿐 (표본 부족) |
| `locationservice.rating` / `review_count` | `locationservicereview` | — | — | 리뷰 2건뿐 (판단 불가) |

## 2. 결론: 코드가 아니라 데이터가 오염된 것

불일치가 **한 방향(카운터 > 실제)으로만** 나타나서 처음에는 "삭제 경로에서 카운터를 안 깎는 버그"로 의심했다. **그 의심은 틀렸다.**

**결정적 근거 — `view_count`:**

- `board.view_count` 총합: **5,154,619**
- `board_view_log` 실제 행 수: **17**

`BoardService` 는 조회 로그 삽입이 성공했을 때만 조회수를 올린다(`insertIgnore(...) > 0` → `incrementViewCount`).
즉 실사용만으로는 로그 17행에 조회수 515만이 나올 수 없다. **이 DB의 데이터는 대량 생성된 더미이고, 카운터가 자식 행과 무관하게 임의로 채워져 있다.**

`meetup.current_participants` 의 차이가 0~7+ 로 균등 분산된 것도 같은 결론을 뒷받침한다 — 로직 버그라면 오프바이원처럼 규칙적인 형태로 나타난다.

**코드 검증 (읽어서 확인):**

| 경로 | 확인 결과 |
|---|---|
| `ReactionService.toggleReaction` | 토글 오프 시 행 삭제 + `likeDelta--` → **대칭. 정합** |
| `CommentService.deleteComment` | `softDelete()` + `adjustCommentCount(-1)` → **대칭. 정합** |
| `CommentService` 복구 경로 | `adjustCommentCount(+1)` → **대칭. 정합** |
| `MeetupRepository` 참가/취소 | 원자적 `+1` / `-1` (`currentParticipants > 0` 가드) → **정합** |

증감은 모두 원자적 UPDATE 쿼리이고 자식 행 변경과 **같은 트랜잭션 안**에 있다.

**배제한 가설:**

- *유저 삭제 시 CASCADE 로 자식 행이 사라지고 카운터만 남았다* → `comment.user_idx`, `board_reaction.user_idx` 의 FK 는 모두 `ON DELETE NO ACTION` 이고, 고아 행은 **0건**이다. 기각.
- *소프트삭제된 댓글이 카운터에 잡혀 있다* → `comment` 25,527행이 **전부** `is_deleted=0 / ACTIVE`. 소프트삭제된 댓글은 하나도 없다. 기각.

## 3. 그래서 진짜 문제는 무엇인가

코드는 정합하지만, **드리프트를 탐지하거나 복구할 장치가 전혀 없다.** 지금 데이터가 어긋나 있다는 사실을 아무도 몰랐던 것이 그 증거다.

반정규화 카운터는 원자적 UPDATE 로도 **완벽히 보장되지 않는다.** 자식 행 삽입과 카운터 증가가 같은 트랜잭션이어도, 운영 중에는 배치 삽입·수동 SQL·데이터 마이그레이션처럼 앱을 우회하는 경로가 반드시 생긴다. **정합성을 보장하는 유일한 방법은 주기적으로 원본과 대조하는 것이다.**

### 권장

1. **드리프트 점검 쿼리를 레포에 두고 주기 실행** — 아래 §4 의 쿼리를 그대로 쓸 수 있다.
2. **현재 더미 데이터의 카운터를 실제 값으로 재계산** — 데모/시연 시 표시되는 좋아요·댓글·참여자 수가 지금은 틀린 값이다.
3. `pet_coin_balance` 는 거래 이력 유저가 2명뿐이라 **이번 측정으로는 안전하다고 말할 수 없다.** 돈이 걸린 필드이므로 별도 검증이 필요하다.

## 4. 재사용 가능한 드리프트 점검 쿼리

```sql
-- board.like_count
SELECT COUNT(*) AS drift FROM board b
LEFT JOIN (SELECT board_idx, COUNT(*) c FROM board_reaction WHERE reaction_type='LIKE' GROUP BY board_idx) r
  ON r.board_idx = b.idx
WHERE b.like_count <> IFNULL(r.c, 0);

-- board.comment_count (소프트삭제 제외)
SELECT COUNT(*) AS drift FROM board b
LEFT JOIN (SELECT board_idx, COUNT(*) c FROM comment WHERE is_deleted = 0 GROUP BY board_idx) cm
  ON cm.board_idx = b.idx
WHERE b.comment_count <> IFNULL(cm.c, 0);

-- meetup.current_participants
SELECT COUNT(*) AS drift FROM meetup m
LEFT JOIN (SELECT meetup_idx, COUNT(*) c FROM meetupparticipants GROUP BY meetup_idx) p
  ON p.meetup_idx = m.idx
WHERE m.current_participants <> IFNULL(p.c, 0);

-- users.pet_coin_balance (최종 거래의 balance_after 와 대조)
SELECT COUNT(*) AS drift FROM users u
JOIN (
  SELECT p.user_idx, p.balance_after AS last_balance
  FROM pet_coin_transaction p
  JOIN (SELECT user_idx, MAX(idx) mx FROM pet_coin_transaction GROUP BY user_idx) m
    ON m.user_idx = p.user_idx AND m.mx = p.idx
) t ON t.user_idx = u.idx
WHERE u.pet_coin_balance <> t.last_balance;
```

## 5. 스키마 형태(정규화 수준)에 대한 결론

`02-normalization-analysis.md` 의 판정은 **유지한다.** 전 도메인이 3NF 를 준수하고, 반정규화는 전부 조회 성능을 위한 **의도적 선택**이며, 다형적 참조(`Report` / `Notification` / `File` 의 `target_type` + `target_idx`)도 실용적 타협으로 타당하다.

**스키마 구조를 바꿀 이유는 이번 측정에서 발견되지 않았다.** 문제는 형태가 아니라 **동기화 검증 장치의 부재**였다.
