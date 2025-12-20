# Meetup 도메인 동시성 제어 개선 제안

## 현재 방식 vs 개선 방식 비교

### ❌ 현재 방식: Pessimistic Lock + currentParticipants 증가

**현재 코드:**
```java
// Pessimistic Lock으로 모임 조회
Meetup meetup = meetupRepository.findByIdWithLock(meetupIdx)
    .orElseThrow(() -> new RuntimeException("모임을 찾을 수 없습니다."));

// 체크 후 증가
if (meetup.getCurrentParticipants() >= meetup.getMaxParticipants()) {
    throw new RuntimeException("모임 인원이 가득 찼습니다.");
}
meetup.setCurrentParticipants(meetup.getCurrentParticipants() + 1);
meetupRepository.save(meetup);
```

**문제점:**
1. **Lock 대기 시간**: 다른 트랜잭션이 Lock을 기다려야 함 (성능 저하)
2. **프로젝트 내 일관성 부족**: 
   - Chat 도메인: `incrementUnreadCount()` - 원자적 UPDATE 쿼리 사용
   - User 도메인: `incrementWarningCount()` - 원자적 UPDATE 쿼리 사용
   - Meetup 도메인: Pessimistic Lock 사용 (일관성 없음)
3. **불필요한 Lock 유지**: 체크와 증가 사이에 Lock이 유지됨
4. **코드 복잡도**: Lock 획득, 체크, 증가 단계가 분리됨

---

### ✅ 개선 방식: 원자적 UPDATE 쿼리 (프로젝트 표준 패턴)

**개선 코드:**
```java
// Repository에 추가
@Modifying
@Query("UPDATE Meetup m SET m.currentParticipants = m.currentParticipants + 1 " +
       "WHERE m.idx = :meetupIdx " +
       "  AND m.currentParticipants < m.maxParticipants")
int incrementParticipantsIfAvailable(@Param("meetupIdx") Long meetupIdx);

// Service에서 사용
if (!meetup.getOrganizer().getIdx().equals(userIdx)) {
    int updated = meetupRepository.incrementParticipantsIfAvailable(meetupIdx);
    if (updated == 0) {
        throw new RuntimeException("모임 인원이 가득 찼습니다.");
    }
}
```

**장점:**
1. **성능 향상**: Lock 대기 시간 없음 (DB 레벨 원자적 처리)
2. **프로젝트 일관성**: Chat, User 도메인과 동일한 패턴
3. **코드 간결성**: 한 번의 쿼리로 체크 + 증가 동시 처리
4. **효율성**: DB 레벨에서 조건부 업데이트로 불필요한 Lock 방지
5. **확장성**: 동시 접근이 많아도 성능 저하 최소화

---

## 프로젝트 내 비교

### Chat 도메인 (원자적 UPDATE 쿼리)
```java
@Modifying
@Query("UPDATE ConversationParticipant p SET p.unreadCount = p.unreadCount + 1 " +
       "WHERE p.conversation.idx = :conversationIdx AND p.user.idx != :senderUserId")
void incrementUnreadCount(@Param("conversationIdx") Long conversationIdx, 
                          @Param("senderUserId") Long senderUserId);
```
✅ **효과**: Lost Update 방지, 성능 우수

### User 도메인 (원자적 UPDATE 쿼리)
```java
@Modifying
@Query("UPDATE Users u SET u.warningCount = u.warningCount + 1 WHERE u.idx = :userId")
int incrementWarningCount(@Param("userId") Long userId);
```
✅ **효과**: 동시성 문제 해결, 성능 우수

### Meetup 도메인 (현재: Pessimistic Lock)
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT m FROM Meetup m WHERE m.idx = :idx")
Optional<Meetup> findByIdWithLock(@Param("idx") Long idx);
```
⚠️ **문제**: Lock 대기로 인한 성능 저하, 프로젝트 내 일관성 부족

---

## 개선 제안

### 1. Repository에 원자적 증가 메서드 추가

```java
// MeetupRepository.java
@Modifying
@Query("UPDATE Meetup m SET m.currentParticipants = m.currentParticipants + 1 " +
       "WHERE m.idx = :meetupIdx " +
       "  AND m.currentParticipants < m.maxParticipants")
int incrementParticipantsIfAvailable(@Param("meetupIdx") Long meetupIdx);
```

### 2. Service 로직 개선

```java
// MeetupService.java
@Transactional
public MeetupParticipantsDTO joinMeetup(Long meetupIdx, String userId) {
    // 모임 조회 (Lock 없이)
    Meetup meetup = meetupRepository.findById(meetupIdx)
            .orElseThrow(() -> new RuntimeException("모임을 찾을 수 없습니다."));

    // 사용자 확인
    Users user = usersRepository.findByIdString(userId)
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

    // 이메일 인증 확인
    if (user.getEmailVerified() == null || !user.getEmailVerified()) {
        throw new EmailVerificationRequiredException("모임 참여를 위해 이메일 인증이 필요합니다.");
    }

    Long userIdx = user.getIdx();

    // 이미 참가했는지 확인
    if (meetupParticipantsRepository.existsByMeetupIdxAndUserIdx(meetupIdx, userIdx)) {
        throw new RuntimeException("이미 참가한 모임입니다.");
    }

    // 주최자가 아닌 경우에만 인원 증가 (원자적 쿼리)
    if (!meetup.getOrganizer().getIdx().equals(userIdx)) {
        int updated = meetupRepository.incrementParticipantsIfAvailable(meetupIdx);
        if (updated == 0) {
            throw new RuntimeException("모임 인원이 가득 찼습니다.");
        }
    }

    // 참가자 추가
    MeetupParticipants participant = MeetupParticipants.builder()
            .meetup(meetup)
            .user(user)
            .joinedAt(LocalDateTime.now())
            .build();

    MeetupParticipants savedParticipant = meetupParticipantsRepository.save(participant);

    return participantsConverter.toDTO(savedParticipant);
}
```

---

## 성능 비교

| 항목 | Pessimistic Lock | 원자적 UPDATE 쿼리 |
|------|-----------------|-------------------|
| **동시성 안전성** | ✅ 보장 | ✅ 보장 |
| **Lock 대기 시간** | ⚠️ 있음 (성능 저하) | ✅ 없음 |
| **쿼리 수** | 2개 (SELECT + UPDATE) | 1개 (UPDATE) |
| **프로젝트 일관성** | ❌ 없음 (Chat/User와 다름) | ✅ 있음 (Chat/User와 동일) |
| **코드 복잡도** | 높음 | 낮음 |
| **동시 접근 성능** | 낮음 (순차 처리) | 높음 (병렬 처리 가능) |

---

## 결론

**원자적 UPDATE 쿼리 방식이 더 좋은 이유:**

1. ✅ **프로젝트 일관성**: Chat, User 도메인과 동일한 패턴
2. ✅ **성능 우수**: Lock 대기 시간 없음
3. ✅ **코드 간결**: 한 번의 쿼리로 처리
4. ✅ **확장성**: 동시 접근이 많아도 성능 저하 최소화
5. ✅ **DB 레벨 보장**: 조건부 업데이트로 안전성 확보

**권장 사항:**
- Pessimistic Lock 제거
- 원자적 UPDATE 쿼리로 변경
- 프로젝트 전반의 동시성 제어 패턴 통일
