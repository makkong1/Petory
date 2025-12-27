# 모임 참가 시 Race Condition 문제 해결

## 📋 요약

**문제**: 동시 참가 시 최대 인원 초과 (3명 제한인데 4명 참가)

**해결**: 원자적 UPDATE 쿼리 + DB 제약조건 + 이벤트 기반 아키텍처
- ✅ Race Condition 완전 해결
- ✅ 프로젝트 일관성 확보 (Chat, User 도메인과 동일한 패턴)
- ✅ DB 레벨 이중 안전장치 (CHECK 제약조건)
- ✅ 핵심 도메인과 파생 도메인 분리 (이벤트 기반)

---

## 1. 문제 상황

### 1.1 발생 원인

동시에 여러 사용자가 참가할 때, `currentParticipants` 체크와 증가 사이에 다른 트랜잭션이 끼어들어 Lost Update 발생.

**시나리오 예시**:
- 모임 최대 인원: 3명
- 모임장 1명 (이미 참가) → `currentParticipants = 1`
- 남은 자리: 2명
- 동시에 3명이 참가 버튼 클릭
- 3명 모두 `currentParticipants (1) < maxParticipants (3)` 체크 통과
- 3명 모두 참가 처리
- **결과**: `currentParticipants = 1 + 3 = 4명` → 최대 인원 초과!

### 1.2 문제점

#### Race Condition으로 인한 데이터 불일치
- **증상**: 최대 인원을 초과하여 참가가 허용됨
- **원인**: 
  - `currentParticipants` 체크와 증가 사이에 다른 트랜잭션이 끼어들 수 있음
  - `setCurrentParticipants(getCurrentParticipants() + 1)`는 원자적 연산이 아님
  - 동시에 여러 요청이 같은 값을 읽고 증가시켜 Lost Update 발생
- **영향**: 
  - 모임 인원 관리 실패
  - 사용자 신뢰도 하락
  - 운영상 문제 발생 가능

### 1.3 Before (문제 코드)

```java
// ⚠️ Race Condition 발생 지점
if (meetup.getCurrentParticipants() >= meetup.getMaxParticipants()) {
    throw new RuntimeException("모임 인원이 가득 찼습니다.");
}
// 여기서 다른 트랜잭션이 끼어들 수 있음!
meetup.setCurrentParticipants(meetup.getCurrentParticipants() + 1);
meetupRepository.save(meetup);
```

**결과**: 3명이 동시 참가 시도 → 모두 체크 통과 → 4명 참가 (최대 3명 초과)

---

## 2. 해결 방법

### 2.1 원자적 UPDATE 쿼리 방식

**Repository 메서드**:
```java
@Modifying
@Query("UPDATE Meetup m SET m.currentParticipants = m.currentParticipants + 1 " +
       "WHERE m.idx = :meetupIdx " +
       "  AND m.currentParticipants < m.maxParticipants")
int incrementParticipantsIfAvailable(@Param("meetupIdx") Long meetupIdx);
```

**Service 로직**:
```java
// 원자적 UPDATE 쿼리로 조건부 증가 (DB 레벨에서 체크 + 증가 동시 처리)
int updated = meetupRepository.incrementParticipantsIfAvailable(meetupIdx);
if (updated == 0) {
    throw new RuntimeException("모임 인원이 가득 찼습니다.");
}
```

**동작 방식**: DB 레벨에서 조건 체크와 증가를 원자적으로 처리하여 Race Condition 완전 방지

### 2.2 DB 제약조건 추가 (이중 안전장치)

```sql
ALTER TABLE meetup 
ADD CONSTRAINT chk_participants 
CHECK (current_participants <= max_participants);
```

**효과**: 애플리케이션 로직을 우회하는 직접 SQL 실행 시에도 데이터 무결성 보장

**주의**: MySQL 8.0.16 이상에서만 적용됨

### 2.3 다른 해결 방법들

#### 1️⃣ **세마포어 (Semaphore)** - 애플리케이션 레벨 동시성 제어

```java
// 각 모임별로 세마포어 생성 (메모리 기반)
private final Map<Long, Semaphore> meetupSemaphores = new ConcurrentHashMap<>();

public MeetupParticipantsDTO joinMeetup(Long meetupIdx, String userId) {
    // 모임별 세마포어 가져오기 (최대 인원수만큼 허용)
    Semaphore semaphore = meetupSemaphores.computeIfAbsent(meetupIdx, 
        idx -> new Semaphore(getMeetupMaxParticipants(idx) - getCurrentParticipants(idx)));
    
    try {
        // 세마포어 획득 시도 (인원이 가득 차면 대기)
        if (!semaphore.tryAcquire(1, TimeUnit.SECONDS)) {
            throw new RuntimeException("모임 인원이 가득 찼습니다.");
        }
        
        // 참가 처리
        // ...
    } catch (InterruptedException e) {
        throw new RuntimeException("참가 처리 중 오류가 발생했습니다.");
    } finally {
        // 실패 시 세마포어 반환
        // 성공 시에는 반환하지 않음 (인원 유지)
    }
}
```

**장점**:
- ✅ 빠른 응답 시간 (메모리 기반)
- ✅ 애플리케이션 레벨에서 즉시 차단 가능

**단점**:
- ❌ **분산 환경 미지원**: 서버가 여러 대면 각 서버별로 세마포어가 독립적으로 동작
- ❌ **서버 재시작 시 초기화**: 메모리 기반이라 재시작 시 상태 손실
- ❌ **DB와 동기화 필요**: 실제 DB 상태와 메모리 상태 불일치 가능
- ❌ **복잡도 증가**: 세마포어 관리, 정리 로직 필요

#### 2️⃣ **Pessimistic Lock (비관적 락)** - DB 레벨 락

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT m FROM Meetup m WHERE m.idx = :meetupIdx")
Optional<Meetup> findByIdWithLock(@Param("meetupIdx") Long meetupIdx);

// 사용
Meetup meetup = meetupRepository.findByIdWithLock(meetupIdx)
    .orElseThrow(() -> new RuntimeException("모임을 찾을 수 없습니다."));

if (meetup.getCurrentParticipants() >= meetup.getMaxParticipants()) {
    throw new RuntimeException("모임 인원이 가득 찼습니다.");
}
meetup.setCurrentParticipants(meetup.getCurrentParticipants() + 1);
meetupRepository.save(meetup);
```

**장점**:
- ✅ 분산 환경 지원 (DB 레벨)
- ✅ 데이터 일관성 보장

**단점**:
- ❌ **성능 저하**: Lock 대기로 인한 블로킹
- ❌ **Deadlock 위험**: 여러 트랜잭션 간 Lock 순서 불일치 시
- ❌ **확장성 제한**: 동시 요청이 많을수록 대기 시간 증가

#### 3️⃣ **Optimistic Lock (낙관적 락)** - 버전 기반

```java
@Entity
public class Meetup {
    @Version
    private Long version; // 버전 필드 추가
    
    // ...
}

// 사용
try {
    Meetup meetup = meetupRepository.findById(meetupIdx).orElseThrow();
    if (meetup.getCurrentParticipants() >= meetup.getMaxParticipants()) {
        throw new RuntimeException("모임 인원이 가득 찼습니다.");
    }
    meetup.setCurrentParticipants(meetup.getCurrentParticipants() + 1);
    meetupRepository.save(meetup); // version 체크
} catch (OptimisticLockingFailureException e) {
    // 재시도 또는 실패 처리
}
```

**장점**:
- ✅ 높은 동시성 (Lock 대기 없음)
- ✅ 분산 환경 지원

**단점**:
- ❌ **재시도 로직 필요**: 충돌 시 재시도 필요
- ❌ **Race Condition 여전히 발생 가능**: 체크-업데이트 사이 다른 트랜잭션 끼어들 수 있음
- ❌ **복잡도 증가**: 재시도 메커니즘 구현 필요

#### 4️⃣ **Redis 분산 락** - 분산 환경용

```java
// Redisson 또는 Lettuce 사용
RLock lock = redissonClient.getLock("meetup:" + meetupIdx);
try {
    if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
        // 참가 처리
    }
} finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

**장점**:
- ✅ **분산 환경 완벽 지원**
- ✅ 빠른 성능 (Redis 메모리 기반)

**단점**:
- ❌ **인프라 의존성**: Redis 추가 필요
- ❌ **복잡도 증가**: Redis 설정, 관리 필요
- ❌ **비용**: Redis 인스턴스 운영 비용

#### 5️⃣ **원자적 UPDATE 쿼리** (현재 사용 중) ⭐

```java
@Modifying
@Query("UPDATE Meetup m SET m.currentParticipants = m.currentParticipants + 1 " +
       "WHERE m.idx = :meetupIdx " +
       "  AND m.currentParticipants < m.maxParticipants")
int incrementParticipantsIfAvailable(@Param("meetupIdx") Long meetupIdx);

// 사용
int updated = meetupRepository.incrementParticipantsIfAvailable(meetupIdx);
if (updated == 0) {
    throw new RuntimeException("모임 인원이 가득 찼습니다.");
}
```

**장점**:
- ✅ **분산 환경 지원** (DB 레벨)
- ✅ **높은 동시성** (Lock 대기 없음)
- ✅ **단순하고 명확**: 체크와 업데이트가 원자적으로 처리
- ✅ **프로젝트 일관성**: Chat, User 도메인과 동일한 패턴

**단점**:
- ❌ 거의 없음 (가장 균형 잡힌 방법)

### 2.4 선택 이유

1. **프로젝트 일관성**: Chat, User 도메인과 동일한 패턴 (가장 중요)
2. **확장성**: 병렬 처리 가능 (Lock 대기 없음)
3. **DB 레벨 보장**: 조건부 업데이트로 안전성 확보
4. **분산 환경 지원**: 여러 서버 환경에서도 안전
5. **단순함**: 복잡한 재시도 로직이나 인프라 추가 불필요


**결론**: 원자적 UPDATE 쿼리가 **분산 환경, 성능, 단순성, 데이터 일관성** 모든 면에서 균형 잡힌 최선의 선택

---

## 3. 트랜잭션 개선: 핵심 도메인과 파생 도메인 분리

### 3.1 문제 상황

모임 생성 후 채팅방 생성 시도 시, 채팅방 생성 실패가 모임 생성까지 롤백하는 문제

**설계 원칙**: **파생 도메인은 실패해도 핵심 도메인을 롤백하면 안 된다.**

### 3.2 해결: 이벤트 기반 아키텍처

**모임 생성 (핵심 도메인)**:
```java
@Transactional
public MeetupDTO createMeetup(...) {
    Meetup savedMeetup = meetupRepository.save(meetup);
    
    // 이벤트 발행 (트랜잭션 커밋 후 비동기 처리)
    eventPublisher.publishEvent(new MeetupCreatedEvent(...));
    
    return converter.toDTO(savedMeetup);
}
```

**채팅방 생성 (파생 도메인)**:
```java
@EventListener
@Async
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void handleMeetupCreated(MeetupCreatedEvent event) {
    try {
        conversationService.createConversation(...);
        conversationService.setParticipantRole(...);
    } catch (Exception e) {
        // 채팅방 생성 실패해도 모임은 이미 생성됨 (롤백되지 않음)
        log.error("채팅방 생성 실패: meetupIdx={}", event.getMeetupIdx(), e);
    }
}
```

**효과**:
- ✅ 핵심 도메인 보장: 모임 생성은 항상 성공
- ✅ 사용자 경험 개선: 모임 생성 즉시 응답, 채팅방은 비동기 생성
- ✅ 확장성: 다른 부가 기능도 이벤트 리스너로 추가 가능

**구현 파일**:
- `MeetupCreatedEvent.java`: 이벤트 클래스
- `MeetupChatRoomEventListener.java`: 이벤트 리스너

---

## 4. 결과

### 4.1 Before/After 비교

| 항목 | Before | After |
|------|--------|-------|
| **Lost Update** | ✅ 발생 (4명 참가) | ❌ 해결 (3명 참가) |
| **인원 초과** | ✅ 발생 | ❌ 해결 |
| **데이터 일치** | ❌ 불일치 | ✅ 일치 |
| **프로젝트 일관성** | - | ✅ 있음 |

### 4.2 테스트 결과

**Before**: 3명 성공, 0명 실패 → 실제 4명 참가 (최대 3명 초과)  
**After**: 2명 성공, 1명 실패 → 실제 3명 참가 (정상)

**테스트 파일**: `MeetupServiceRaceConditionTest.java`

---

## 5. 핵심 포인트

### 적용된 패턴
- ✅ **원자적 UPDATE 쿼리**: 조건부 업데이트가 필요한 경우
- ✅ **DB 제약조건**: 최종 안전망
- ✅ **이벤트 기반 아키텍처**: 핵심 도메인과 파생 도메인 분리

### 로깅 전략
- `INFO`: 정상 흐름
- `WARN`: 예상 가능한 실패 (인원 초과 등)
- `ERROR`: 데이터 정합성 문제
