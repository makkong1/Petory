# 펫케어 거래 확정 동시성 문제 시퀀스 다이어그램

## 포트폴리오용 간결 버전

### 비관적 락 적용 전 (Race Condition 발생)

```javascript
// 시퀀스 다이어그램 (동시성 문제 발생)
const raceConditionSequence = `sequenceDiagram
    participant UserA as 사용자 A
    participant UserB as 사용자 B
    participant Service as ConversationService
    participant DB as MySQL

    Note over UserA,UserB: 동시에 거래 확정 버튼 클릭

    par 동시 요청
        UserA->>Service: confirmCareDeal() (Tx A)
        UserB->>Service: confirmCareDeal() (Tx B)
    end

    Service->>DB: Tx A: 내 상태 '확정' 변경
    Service->>DB: Tx B: 내 상태 '확정' 변경

    Note over Service,DB: 격리 수준(Isolation)으로 인해<br/>상대방의 변경사항 안 보임

    Service->>DB: Tx A: 전체 확정 여부 확인? -> False (B 미확정)
    Service->>DB: Tx B: 전체 확정 여부 확인? -> False (A 미확정)

    Service-->>UserA: 완료 (상태 변경 없음)
    Service-->>UserB: 완료 (상태 변경 없음)

    Note over DB: 결과: 둘 다 확정했으나<br/>상태는 여전히 OPEN (Stuck)`;
```

### 비관적 락 적용 후 (동시성 문제 해결)

```javascript
// 시퀀스 다이어그램 (비관적 락 적용)
const pessimisticLockSequence = `sequenceDiagram
    participant UserA as 사용자 A
    participant UserB as 사용자 B
    participant Service as ConversationService
    participant DB as MySQL

    Note over UserA,UserB: 동시에 거래 확정 버튼 클릭

    UserA->>Service: confirmCareDeal() (Tx A)
    Service->>DB: SELECT ... FOR UPDATE (Lock 획득)
    
    UserB->>Service: confirmCareDeal() (Tx B)
    Service->>DB: SELECT ... FOR UPDATE (Lock 대기)
    
    Note over Service,DB: Tx A 먼저 수행
    Service->>DB: Tx A: 내 상태 '확정' 변경
    Service->>DB: Tx A: 전체 확정 여부 확인? -> False
    Service->>DB: Tx A: 커밋 & Lock 반납
    
    Note over Service,DB: Tx B 수행 (대기 해제)
    Service->>DB: Tx B: Lock 획득 (최신 데이터 조회)
    Service->>DB: Tx B: 내 상태 '확정' 변경
    Service->>DB: Tx B: 전체 확정 여부 확인? -> True (A 확정 보임)
    
    Service->>DB: Tx B: CareRequest 상태 IN_PROGRESS 변경
    Service-->>UserB: 완료 및 상태 변경 성공`;
```

---

## 상세 버전 (문서용)

### 문제 상황: Race Condition에 의한 상태 누락 (Stuck State)

```mermaid
sequenceDiagram
    participant UserA as 사용자 A (Requester)
    participant UserB as 사용자 B (Provider)
    participant Service as ConversationService
    participant Repo as Repository
    participant DB as MySQL

    Note over UserA,UserB: 거의 동시에 '거래 확정' 요청

    par Parallel Execution
        UserA->>Service: confirmCareDeal(idx, A)
        UserB->>Service: confirmCareDeal(idx, B)
    end

    Note over Service,DB: Transaction A (Tx A) 시작
    Service->>Repo: findById(idx)
    Repo->>DB: SELECT (Snapshot Read)
    DB-->>Service: Conversation (A=False, B=False)

    Note over Service,DB: Transaction B (Tx B) 시작
    Service->>Repo: findById(idx)
    Repo->>DB: SELECT (Snapshot Read)
    DB-->>Service: Conversation (A=False, B=False)

    Service->>Repo: Tx A: save(A=True)
    Note over DB: Tx A 변경사항 (Commit 전이라 B에게 안 보임)

    Service->>Repo: Tx B: save(B=True)
    Note over DB: Tx B 변경사항 (Commit 전이라 A에게 안 보임)

    Service->>Repo: Tx A: checkAllConfirmed()
    Repo->>DB: SELECT participants...
    DB-->>Service: A=True, B=False -> Result: False
    Note over Service: Tx A 종료 (상태 변경 로직 SKIP)

    Service->>Repo: Tx B: checkAllConfirmed()
    Repo->>DB: SELECT participants...
    DB-->>Service: A=False, B=True -> Result: False
    Note over Service: Tx B 종료 (상태 변경 로직 SKIP)

    Service->>DB: Tx A Commit
    Service->>DB: Tx B Commit

    Note over DB: 최종 상태: A=True, B=True<br/>하지만 CareRequest는 여전히 OPEN
```

### 해결: Pessimistic Lock & Persistent Handling

```mermaid
sequenceDiagram
    participant UserA as 사용자 A (Requester)
    participant UserB as 사용자 B (Provider)
    participant Service as ConversationService
    participant Repo as Repository
    participant DB as MySQL

    UserA->>Service: confirmCareDeal(idx, A)
    Note over Service,DB: 1. Tx A: 비관적 락(Pessimistic Lock) 시도
    Service->>Repo: findByIdWithLock(idx)
    Repo->>DB: SELECT ... FOR UPDATE
    DB-->>Service: Lock 획득, 조회 성공

    UserB->>Service: confirmCareDeal(idx, B)
    Note over Service,DB: 2. Tx B: 비관적 락 시도 -> 대기
    Service->>Repo: findByIdWithLock(idx)
    Repo->>DB: SELECT ... FOR UPDATE (WAIT)

    Note over Service: 3. Tx A 로직 수행
    Service->>Repo: save(A=True)
    Service->>Repo: checkAllConfirmed() -> False
    Service->>DB: Tx A Commit & Lock Release

    Note over DB: 4. Lock 해제됨 -> Tx B 진행

    DB-->>Service: Tx B: Lock 획득, 조회 성공 (A=True 반영됨)
    Service->>Repo: save(B=True)
    
    Service->>Repo: checkAllConfirmed()
    Repo->>DB: SELECT participants...
    DB-->>Service: A=True, B=True -> Result: True
    
    Note left of Service: 5. 조건 만족 -> 후속 로직 실행
    
    Note over Service: TransientObjectException 방지 로직
    Service->>Repo: getReferenceById(reqIdx) -> Proxy
    Service->>Repo: saveAndFlush(newApplication)
    Service->>Repo: careRequest.setStatus(IN_PROGRESS)
    
    Service->>DB: Tx B Commit (최종 상태 변경 완료)
```
