# N+1 해결 전략

> Petory 프로젝트에서 도메인별로 적용한 N+1 해결 방법을 정리한 문서입니다.

## 1. 해결 방법 개요

| 방법 | 설명 | 적용 상황 |
|------|------|-----------|
| **Fetch Join** | `JOIN FETCH`로 연관 엔티티를 한 번에 조회 | 단건 상세 조회, 단일 컬렉션 |
| **Batch Size** | `@BatchSize(size=N)`으로 N개씩 IN 절 배치 조회 | 페이징 목록, 다중 컬렉션 |

### 왜 이렇게 나눴는지

- **Fetch Join**: 쿼리 1번으로 끝. 단, **컬렉션 2개 이상**을 FETCH JOIN하면 카테시안 곱 발생. 페이징 시 Hibernate 제약(경고, 잘못된 LIMIT) 있음.
- **Batch Size**: 여러 컬렉션·페이징이 있어도 안전. 쿼리는 1 + ceil(N/50) 수준으로 수렴. 카테시안 곱 없음.

**결론**: 단건·단일 컬렉션 → Fetch Join. 페이징·다중 컬렉션 → Batch Size.

---

## 2. 규칙 (Fetch 최적화 전략)

| 조회 유형 | 전략 | 적용 방법 |
|-----------|------|-----------|
| **단건 상세 조회** | Fetch Join | `findByIdWithXXX()` 메서드에 `JOIN FETCH` |
| **페이징 목록 조회** | Batch Size | OneToMany 컬렉션에 `@BatchSize(size=50)` |

---

## 3. 도메인별 적용 현황

### User
- **Users**: `@BatchSize(size=50)` — socialUsers, Meetup.organizer 등 ManyToOne proxy 배치 로드
- **Pet**: `@BatchSize(size=50)` — vaccinations (PetVaccination)
- **SocialUser**: Users 조회 시 N+1 → Users에 @BatchSize로 해결

### Board
- **Board**: comments — Batch Size 또는 별도 페이징 조회
- **Comment**: board, user — Fetch Join 또는 Batch Size
- **BoardReaction, CommentReaction**: board_idx, user_idx — 단건/목록 조회 시 Fetch Join

### Care
- **CareRequest**: `@BatchSize(size=50)` — applications
- **CareApplication**: careRequest, provider — Fetch Join (단건) / Batch Size (목록)
- **CareRequestComment**: careRequest, user — Fetch Join

### Meetup
- **Meetup**: `@BatchSize(size=50)` — participants
- **MeetupParticipants**: meetup, user — Meetup 목록 조회 시 Batch Size

### Chat
- **Conversation**: participants, messages — Batch Size 또는 별도 조회
- **ConversationParticipant**: conversation, user — Fetch Join
- **ChatMessage**: conversation, sender — Fetch Join (단건) / 페이징 조회

### Payment
- **PetCoinTransaction**: `@EntityGraph(attributePaths = "user")` — user Fetch Join
- **PetCoinEscrow**: careRequest, careApplication, requester, provider — Fetch Join

### Location
- **LocationService**: reviews — Batch Size 또는 별도 조회
- **LocationServiceReview**: service, user — Fetch Join

---

## 4. 참고 문서

- [docs/refactoring/fetch-optimization/README.md](../../refactoring/fetch-optimization/README.md) — Fetch 최적화 규칙
- [docs/refactoring/fetch-optimization/user/](../../refactoring/fetch-optimization/user/) — User 도메인
- [docs/refactoring/fetch-optimization/board/](../../refactoring/fetch-optimization/board/) — Board 도메인
- [docs/refactoring/fetch-optimization/care/](../../refactoring/fetch-optimization/care/) — Care 도메인
- [docs/refactoring/fetch-optimization/meetup/](../../refactoring/fetch-optimization/meetup/) — Meetup 도메인
- [docs/refactoring/fetch-optimization/payment/](../../refactoring/fetch-optimization/payment/) — Payment 도메인
- [docs/refactoring/fetch-optimization/location/](../../refactoring/fetch-optimization/location/) — Location 도메인
