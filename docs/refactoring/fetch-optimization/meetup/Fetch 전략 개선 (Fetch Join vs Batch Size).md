# Meetup 도메인 Fetch 전략 개선

> **규칙**: 단건 상세 → Fetch Join / 페이징 목록 → Batch Size

---

## 요약

| 구분 | 대상 | 전략 | 상태 |
|------|------|------|------|
| Meetup 단건 상세 | `getMeetupById` | Fetch Join | ✅ 이미 적용됨 (findByIdWithDetails) |
| Meetup 목록 | `getAllMeetups`, `getNearbyMeetups`, `getMeetupsByLocation` 등 | Fetch Join + Batch Size | ✅ 적용됨 |
| Meetup 단건 수정/참가/취소 | `updateMeetup`, `joinMeetup`, `cancelMeetupParticipation` | Fetch Join | ✅ 적용됨 |
| MeetupParticipants 목록 | `getMeetupParticipants` | Fetch Join | ✅ 적용됨 |

---

## 1. Meetup (모임)

### 1.1 단건 상세 — ✅ 이미 적용됨

**대상**
- `getMeetupById(meetupIdx)` — `GET /api/meetups/{meetupIdx}`

**현재 상태**
- `findByIdWithDetails` — organizer, participants, p.user 모두 Fetch Join ✓

### 1.2 목록 조회 — ✅ 적용됨

**적용 내용**
- `findByOrganizerIdx`, `findByLocationRange`, `findByDateBetween`, `findByKeyword`, `findAvailableMeetups`에 `JOIN FETCH m.organizer` 추가
- `Meetup.participants`에 `@BatchSize(size=50)` 추가
- `Users` 엔티티에 `@BatchSize(size=50)` 추가 (organizer, participant.user proxy 배치 로드)
- `findNearbyMeetups`는 native query → @BatchSize로 완화

### 1.3 단건 수정/참가/취소 — ✅ 적용됨

**적용 내용**
- `updateMeetup`: `findByIdWithDetails` 사용 (toDTO에 organizer, participants 필요)
- `joinMeetup`, `cancelMeetupParticipation`: `findByIdWithOrganizer` 사용

---

## 2. MeetupParticipants (모임 참여자)

### 2.1 모임별 참여자 목록 — ✅ 적용됨

**적용 내용**
- `findByMeetupIdxOrderByJoinedAtAsc`에 `JOIN FETCH mp.user` 추가

---

## 3. 적용 완료 내역

| 파일 | 적용 내용 |
|------|----------|
| `Meetup.java` | `@BatchSize(size=50)` on participants |
| `Users.java` | `@BatchSize(size=50)` on entity (organizer, participant.user proxy) |
| `SpringDataJpaMeetupRepository` | findByOrganizerIdx, findByLocationRange, findByDateBetween, findByKeyword, findAvailableMeetups에 `JOIN FETCH m.organizer` 추가 |
| `SpringDataJpaMeetupRepository` | `findByIdWithOrganizer(Long idx)` 추가 |
| `MeetupRepository`, `JpaMeetupAdapter` | `findByIdWithOrganizer` 시그니처/구현 |
| `MeetupService.updateMeetup` | `findByIdWithDetails` 사용 |
| `MeetupService.joinMeetup`, `cancelMeetupParticipation` | `findByIdWithOrganizer` 사용 |
| `SpringDataJpaMeetupParticipantsRepository` | `findByMeetupIdxOrderByJoinedAtAsc`에 `JOIN FETCH mp.user` 추가 |

---

## 4. 참고

- `findNearbyMeetups`는 native query (Haversine) 사용 → JPQL Fetch Join 불가
- `@BatchSize` on Meetup.participants로 participants N+1 완화
- Users 엔티티에 `@BatchSize` 있으면 participant.user 배치 로드에 활용 가능
