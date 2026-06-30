# Step 5 — Meetup 주최자/참가자 제재 정책

## 목적

Meetup 도메인에서 제재 정책을 구현한다:
1. 신규 모임 생성 / 참가 차단 (제재 사용자)
2. 주최자가 제재된 RECRUITING 모임 취소 (SUSPENDED / BANNED 모두)
3. 참가자가 제재되면 참가 취소 + 채팅 참여 비활성화

**전제**: Step 2(UserSanctionAppliedEvent)가 완료되어 이벤트 클래스가 존재해야 한다.

## 배경 정책

- Care와 달리 Meetup 주최자 제재 시 SUSPENDED/BANNED 모두 RECRUITING 모임을 취소한다. 정지 해제 후 자동 복구 없음.
- 참가자 제재 시: 참가 row의 상태를 CANCELLED로 변경, 채팅 참여자 상태를 LEFT로 변경.
- CLOSED(종료된) 모임은 변경 없이 이력 유지.

## 변경 파일

### 1. `MeetupForbiddenException.java` — 팩토리 메서드 추가
경로: `backend/main/java/com/linkup/Petory/domain/meetup/exception/MeetupForbiddenException.java`

기존 팩토리 메서드 아래에 추가:
```java
public static MeetupForbiddenException sanctioned() {
    return new MeetupForbiddenException("제재된 사용자는 이 작업을 수행할 수 없습니다.");
}
```

`MeetupForbiddenException`이 `ApiException`을 상속하고 HTTP 403을 반환하는지 확인 후 동일 패턴 적용.

### 2. `MeetupService.java` — createMeetup / joinMeetup 제재 차단
경로: `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupService.java`

**createMeetup()**: 사용자 로드 직후 제재 검사 추가:
```java
// usersRepository.findActiveByIdString(userId) 또는 findById() 직후
if (organizer.isSanctioned()) {
    throw MeetupForbiddenException.sanctioned();
}
```

**joinMeetup()** (또는 참가 관련 메서드): 참가자 로드 직후 제재 검사 추가:
```java
if (user.isSanctioned()) {
    throw MeetupForbiddenException.sanctioned();
}
```

실제 메서드명은 `MeetupService`를 읽고 `createMeetup` / `joinMeetup` 또는 이에 준하는 참가 처리 메서드를 확인할 것.

### 3. `MeetupRepository.java` — 이벤트 리스너용 쿼리 추가
경로: `backend/main/java/com/linkup/Petory/domain/meetup/repository/MeetupRepository.java`

인터페이스에 추가:
```java
/** 이벤트 리스너용: 특정 주최자의 RECRUITING 모임 목록 */
List<Meetup> findRecruitingByOrganizerId(Long organizerIdx);
```

### 4. `JpaMeetupAdapter.java` — 구현 추가
경로: `backend/main/java/com/linkup/Petory/domain/meetup/repository/JpaMeetupAdapter.java`

```java
@Override
public List<Meetup> findRecruitingByOrganizerId(Long organizerIdx) {
    return springRepository.findRecruitingByOrganizerIdx(organizerIdx);
}
```

### 5. `SpringDataJpaMeetupRepository.java` — Spring Data JPA 메서드 추가
```java
@Query("SELECT m FROM Meetup m WHERE m.organizer.idx = :organizerIdx AND m.status = 'RECRUITING' AND (m.isDeleted = false OR m.isDeleted IS NULL)")
List<Meetup> findRecruitingByOrganizerIdx(@Param("organizerIdx") Long organizerIdx);
```

`MeetupStatus.RECRUITING` 값이 string으로 `"RECRUITING"`인지 확인할 것. `@Enumerated(EnumType.STRING)` 사용 중이면 그대로 사용 가능.

### 6. `MeetupParticipantsRepository.java` — 이벤트 리스너용 쿼리 추가
경로: `backend/main/java/com/linkup/Petory/domain/meetup/repository/MeetupParticipantsRepository.java`

인터페이스에 추가:
```java
/** 이벤트 리스너용: 특정 참가자의 활성 참가 row 목록 */
List<MeetupParticipants> findActiveByUserId(Long userId);
```

어댑터와 Spring Data JPA 구현도 추가:
```java
// SpringDataJpaMeetupParticipantsRepository 또는 동일 파일에
@Query("SELECT mp FROM MeetupParticipants mp WHERE mp.user.idx = :userId AND mp.status = 'ACTIVE'")
List<MeetupParticipants> findActiveParticipantsByUserId(@Param("userId") Long userId);
```

`MeetupParticipants` 엔티티에 `status` 필드가 없다면 `isDeleted = false` 등의 조건으로 대체. 실제 엔티티 구조 확인 후 조정.

### 7. `ConversationParticipantRepository.java` — 제재 비활성화 메서드 추가 (옵션)
경로: `backend/main/java/com/linkup/Petory/domain/chat/repository/ConversationParticipantRepository.java`

참가자 상태 변경을 위한 bulk update 추가 (해당 repository 인터페이스/어댑터에 맞게):
```java
/** Meetup 채팅 참여 비활성화: 특정 사용자의 GROUP 채팅방 참여자 상태 → LEFT */
void deactivateMeetupParticipant(Long userIdx);
```

구현:
```java
@Modifying
@Query("UPDATE ConversationParticipant cp SET cp.status = 'LEFT', cp.leftAt = :now " +
       "WHERE cp.user.idx = :userIdx AND cp.conversation.type = 'GROUP' AND cp.status = 'ACTIVE'")
void deactivateMeetupParticipantByUserId(@Param("userIdx") Long userIdx, @Param("now") LocalDateTime now);
```

`ConversationType.GROUP`이 meetup 채팅방 타입인지 확인. 다른 타입이면 해당 값으로 교체.

### 8. `UserSanctionMeetupEventListener.java` — 이벤트 리스너 생성
경로: `backend/main/java/com/linkup/Petory/domain/meetup/event/UserSanctionMeetupEventListener.java`

**신규 파일**:
```java
package com.linkup.Petory.domain.meetup.event;

import com.linkup.Petory.domain.chat.repository.ConversationParticipantRepository;
import com.linkup.Petory.domain.meetup.entity.Meetup;
import com.linkup.Petory.domain.meetup.entity.MeetupParticipants;
import com.linkup.Petory.domain.meetup.entity.MeetupStatus;
import com.linkup.Petory.domain.meetup.repository.MeetupParticipantsRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupRepository;
import com.linkup.Petory.domain.user.event.UserSanctionAppliedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserSanctionMeetupEventListener {

    private final MeetupRepository meetupRepository;
    private final MeetupParticipantsRepository participantsRepository;
    private final ConversationParticipantRepository conversationParticipantRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserSanctionApplied(UserSanctionAppliedEvent event) {
        try {
            cancelOrganizerMeetups(event.userId());
            cancelParticipation(event.userId());
        } catch (Exception e) {
            log.error("제재 이벤트 Meetup 후속 처리 실패 (관리자 수동 재처리 필요): userId={}, error={}",
                    event.userId(), e.getMessage(), e);
        }
    }

    private void cancelOrganizerMeetups(Long userId) {
        // SUSPENDED/BANNED 모두 RECRUITING 모임 취소
        List<Meetup> recruitingMeetups = meetupRepository.findRecruitingByOrganizerId(userId);
        for (Meetup meetup : recruitingMeetups) {
            meetup.setStatus(MeetupStatus.CANCELLED);
            meetupRepository.save(meetup);
            log.info("제재 사용자 RECRUITING 모임 취소: meetupId={}, organizerId={}", meetup.getIdx(), userId);
        }
    }

    private void cancelParticipation(Long userId) {
        List<MeetupParticipants> participations = participantsRepository.findActiveByUserId(userId);
        for (MeetupParticipants mp : participations) {
            // 참가 취소 처리 (엔티티 구조에 따라 status 또는 isDeleted 사용)
            mp.setStatus(/* CANCELLED or LEFT */ null); // 실제 enum 값 확인 후 교체
            participantsRepository.save(mp);
            log.info("제재 참가자 모임 참가 취소: meetupId={}, userId={}", mp.getMeetup().getIdx(), userId);
        }
        // 모임 채팅방 참여 비활성화
        conversationParticipantRepository.deactivateMeetupParticipant(userId);
        log.info("제재 참가자 채팅 참여 비활성화 완료: userId={}", userId);
    }
}
```

## 주의사항

- `MeetupParticipants.status` 필드의 실제 enum 타입과 값을 확인한다. 파일이 없는 경우 `MeetupParticipantStatus` 또는 유사한 enum을 찾아야 한다. 없으면 `isDeleted = true` 방식으로 처리.
- `Meetup.setStatus()`가 setter로 열려 있는지 확인. `@Setter` 없으면 엔티티에 `cancel()` 메서드를 추가.
- 모임 채팅방 타입이 `ConversationType.GROUP`인지 실제 entity를 읽어 확인.
- `ConversationParticipantRepository.deactivateMeetupParticipant()` 메서드는 해당 repo의 포트 인터페이스와 어댑터 구조에 맞게 추가해야 한다.

## AC (Acceptance Criteria)

```bash
# 컴파일 통과
./gradlew compileJava

# 확인 포인트:
# - 제재 사용자의 모임 생성/참가 시 403
# - 주최자 제재 이벤트 후 RECRUITING 모임이 CANCELLED로 전환
# - 참가자 제재 이벤트 후 참가 row가 취소 처리, 채팅 참여 비활성화
```
