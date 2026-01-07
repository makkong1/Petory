# 펫케어 거래 확정 동시성 문제 (Race Condition)

## 📋 문제 개요

**도메인**: Care (펫 케어)  
**위치**: `ConversationService.confirmCareDeal()`  
**문제 유형**: Race Condition (동시성 문제)  
**현상**: 상태 변경 누락 (Stuck State)  
**심각도**: **높음** (발생 시 거래가 다음 단계로 넘어가지 않음, 사용자 클레임 유발)

## 🔍 문제 상황

펫케어 서비스는 채팅방의 **두 사용자(요청자, 제공자)가 모두 '거래 확정' 버튼을 눌렀을 때** 자동으로 다음 로직(`CareRequest` 상태 변경)을 수행합니다.

### 현재 구현

```java
// ConversationService.confirmCareDeal()
@Transactional
public void confirmCareDeal(Long conversationIdx, Long userId) {
    // 1. 내 상태를 '확정'으로 변경
    participant.setDealConfirmed(true);
    participantRepository.save(participant);
    
    // 2. 채팅방의 모든 참여자 상태 조회
    List<ConversationParticipant> allParticipants = participantRepository
        .findByConversationIdxAndStatus(conversationIdx, ParticipantStatus.ACTIVE);
    
    // 3. "모두 확정했는지" 검사
    boolean allConfirmed = allParticipants.stream()
        .allMatch(p -> Boolean.TRUE.equals(p.getDealConfirmed()));
    
    // 4. 모두 확정했다면 후속 처리 (상태 변경)
    if (allConfirmed && allParticipants.size() == 2) {
        processCareDealConfirmation(...); // CareRequest 상태 변경 로직
    }
}
```

## ⚠️ 원인 분석

### 잘못된 가정
기존에는 "동시에 실행되면 두 번 실행될 것(중복 실행)"이라고 예상했으나, 실제로는 **DB 트랜잭션 격리 수준(Isolation Level)**으로 인해 **로직이 실행되지 않는(Skip)** 문제가 발생할 수 있습니다.

### 실제 발생 시나리오 (Stuck State)
MySQL의 기본 격리 수준인 `REPEATABLE READ` (또는 `READ COMMITTED`) 환경에서 두 사용자가 거의 동시에 버튼을 눌렀을 때:

1. **Transaction A (사용자 A)** 시작
   - `participantA.setDealConfirmed(true)` 수행
   - 아직 커밋되지 않음.
   
2. **Transaction B (사용자 B)** 시작
   - `participantB.setDealConfirmed(true)` 수행
   - 아직 커밋되지 않음.

3. **Transaction A**가 참여자 목록 조회 (`findBy...`)
   - **A**: `True` (자신의 변경사항 보임)
   - **B**: `False` (**B의 변경사항은 커밋되지 않았으므로 보이지 않음**)
   - 결과: `allConfirmed = False`
   - **조치: 후속 처리 없이 종료**

4. **Transaction B**가 참여자 목록 조회
   - **A**: `False` (**A의 변경사항은 B에게 보이지 않음** - 격리성)
   - **B**: `True` (자신의 변경사항 보임)
   - 결과: `allConfirmed = False`
   - **조치: 후속 처리 없이 종료**

5. **Transaction A, B 커밋**
   - DB 상태: **A=True, B=True**

### 결과
- DB상으로는 두 사용자 모두 "확정" 상태가 됨.
- 하지만 코드상의 트리거(`if (allConfirmed)`)는 양쪽 트랜잭션 모두에서 `False`로 평가되어 실행되지 않음.
- **최종 상태**: `CareRequest`는 여전히 `OPEN` 상태로 남음. 사용자는 "완료되었다"고 표시되는데 진행이 안 되는 상황 발생.

## 🔧 해결 방안

이 문제는 **Check-Then-Act** 패턴에서 "상대방의 상태"를 읽는 시점의 데이터 일관성이 보장되지 않아 발생합니다. 해결을 위해서는 **상위 엔티티(Conversation)에 락을 걸어** 확정 로직을 순차적으로 처리해야 합니다.

### 1. 비관적 락 (Pessimistic Lock) 적용

`Conversation`을 조회할 때 `PESSIMISTIC_WRITE` 락을 겁니다. 이렇게 하면 한 명의 처리가 끝나고 커밋될 때까지 다른 한 명은 대기하게 되며, 대기가 풀린 후에는 **커밋된 최신 데이터**를 읽게 됩니다.

#### Repository 수정

```java
// ConversationRepository.java
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Conversation c WHERE c.idx = :idx")
    Optional<Conversation> findByIdWithLock(@Param("idx") Long idx);
}
```

### 2. Service 로직 구현 (동시성 및 영속성 처리)

단순히 락만 거는 것이 아니라, **자식 엔티티(`CareApplication`) 생성 시 발생하는 `TransientObjectException`**을 해결하기 위해 영속성 컨텍스트 동기화(`saveAndFlush`)와 프록시 참조(`getReferenceById`)를 적용했습니다.

```java
// ConversationService.java
@Transactional
public void confirmCareDeal(Long conversationIdx, Long userId) {
    // 1. 비관적 락으로 조회 (순차 처리 보장)
    Conversation conversation = conversationRepository.findByIdWithLock(conversationIdx)
            .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

    // ... (본인 확인 및 중복 확정 체크) ...

    // 2. 내 상태 '확정'으로 변경
    participant.setDealConfirmed(true);
    participantRepository.save(participant);

    // 3. 전체 확정 여부 체크
    List<ConversationParticipant> allParticipants = participantRepository
            .findByConversationIdxAndStatus(conversationIdx, ParticipantStatus.ACTIVE);
            
    // 4. 양쪽 모두 확정 시 후속 처리
    boolean allConfirmed = allParticipants.stream().allMatch(p -> Boolean.TRUE.equals(p.getDealConfirmed()));
    if (allConfirmed && allParticipants.size() == 2) {
        
        // ... (Provider 식별 로직) ...

        if (existingApplication == null) {
            // [중요] TransientObjectException 방지: Proxy 객체 조회
            CareRequest careRequestRef = careRequestRepository.getReferenceById(relatedIdx);
            
            CareApplication newApplication = CareApplication.builder()
                    .careRequest(careRequestRef) // Proxy 사용
                    .provider(...)
                    .status(CareApplicationStatus.ACCEPTED)
                    .build();
            
            // [중요] 즉시 Flush하여 DB 반영 (Lock 보유 중 처리)
            careApplicationRepository.saveAndFlush(newApplication);
        }
        
        // CareRequest 상태 변경
        careRequest.setStatus(CareRequestStatus.IN_PROGRESS);
        careRequestRepository.save(careRequest);
    }
}
```

## ✅ 적용 완료 및 검증

### 테스트 결과 (`CareDealConcurrencyTest`)
- **시나리오**: 두 명의 사용자(Requester, Provider)가 동시에 `confirmCareDeal`을 호출.
- **검증 항목**:
    1. 두 사용자 모두 `dealConfirmed = true` 상태인가?
    2. `CareRequest` 상태가 `OPEN`에서 `IN_PROGRESS`로 정상 변경되었는가? (Race Condition 발생 시 OPEN으로 남음)
- **결과**: 테스트 **통과 (Passed)**.
    - 초기에는 `TransientObjectException` 및 `Deadlock` 이슈가 있었으나, `saveAndFlush` 및 로직 개선으로 해결됨.
    - 동시 요청 시에도 데이터 정합성이 완벽하게 보장됨을 확인.


