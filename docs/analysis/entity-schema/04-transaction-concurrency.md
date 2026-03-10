# 트랜잭션 & 동시성 제어

> Petory 프로젝트에서 적용한 트랜잭션 관리와 동시성 제어 사례를 정리한 문서입니다.

## 1. 트랜잭션 관리

### 기본 전략

| 항목 | 적용 |
|------|------|
| **트랜잭션 경계** | Service 레이어 `@Transactional` |
| **읽기 전용** | `@Transactional(readOnly = true)` 기본값 — 쓰기 락 미발생 |
| **쓰기 작업** | `@Transactional` (readOnly = false) 명시 |

### 사례

| 도메인 | 사례 | 효과 |
|--------|------|------|
| Board | 게시글 삭제 시 댓글 일괄 soft delete | 원자적 처리, 실패 시 롤백 |
| Comment | 댓글 추가 + commentCount 증가 | 같은 트랜잭션 내 동기화 |
| CareRequest | 펫 소유자 검증 + 요청 저장 | 검증 실패 시 전체 롤백 |
| Board | 조회수 증가 + BoardViewLog 저장 | 중복 조회 방지 로직과 함께 처리 |

---

## 2. 동시성 제어

### 2.1 DB 제약조건 활용

| 테이블 | 제약 | 목적 |
|--------|------|------|
| board_reaction | UK(board_idx, user_idx) | 좋아요/싫어요 중복 방지 |
| comment_reaction | UK(comment_idx, user_idx) | 댓글 반응 중복 방지 |
| board_view_log | UK(board_id, user_id) | 사용자당 1회 조회수 |
| report | UK(target_type, target_idx, reporter_idx) | 동일 대상 중복 신고 방지 |
| meetupparticipants | PK(meetup_idx, user_idx) | 중복 참여 방지 |

### 2.2 원자적 UPDATE (Lost Update 방지)

| 도메인 | 쿼리 패턴 | 목적 |
|--------|-----------|------|
| Users | `UPDATE users SET warning_count = warning_count + 1 WHERE idx = ?` | 경고 횟수 원자적 증가 |
| Meetup | `UPDATE meetup SET current_participants = current_participants + 1 WHERE idx = ? AND current_participants < max_participants` | 모임 참여 인원 원자적 증가 + 최대 인원 체크 |

### 2.3 비관적 락 (Pessimistic Lock)

| 도메인 | 적용 | 목적 |
|--------|------|------|
| Care (거래 확정) | `SELECT ... FOR UPDATE` | 양쪽 확정 시 CareRequest 상태 변경 Race Condition 방지 |

**시퀀스**: [docs/performance/care-deal-concurrency-sequence.md](../../performance/care-deal-concurrency-sequence.md)

### 2.4 개선 필요

| 항목 | 현재 | 개선 방안 |
|------|------|-----------|
| Board.commentCount | 메모리에서 +1 후 save | `UPDATE board SET comment_count = comment_count + 1` |
| Board.likeCount/dislikeCount | 메모리 업데이트 | 원자적 UPDATE 쿼리 검토 |

---

## 3. 격리 수준

- **기본**: MySQL InnoDB `REPEATABLE_READ`
- **필요 시**: `@Transactional(isolation = Isolation.READ_COMMITTED)` 등 명시

---

## 4. 참고 문서

- [docs/concurrency/transaction-concurrency-cases.md](../../concurrency/transaction-concurrency-cases.md) — 상세 사례 및 코드
- [docs/performance/care-deal-concurrency-sequence.md](../../performance/care-deal-concurrency-sequence.md) — 펫케어 거래 확정 동시성
- [docs/troubleshooting/care/care-deal-confirmation-race-condition.md](../../troubleshooting/care/care-deal-confirmation-race-condition.md) — 거래 확정 Race Condition
- [docs/troubleshooting/meetup/race-condition-participants.md](../../troubleshooting/meetup/race-condition-participants.md) — 모임 참여 Race Condition
- [docs/refactoring/payment/petcoin-service-race-condition.md](../../refactoring/payment/petcoin-service-race-condition.md) — 펫코인 Race Condition
