# Step 4 — Care 요청자/제공자 제재 정책

## 목적

Care 도메인에서 제재 정책을 구현한다:
1. 신규 케어 요청/댓글/지원 생성 차단 (제재 사용자)
2. nearby 조회에서 제재 사용자 케어 비노출 (기존 JPQL 쿼리는 이미 처리, native 쿼리 누락 수정)
3. 자동 완료 스케줄러에서 IN_PROGRESS 케어 제재 관련 보류
4. BANNED 사용자의 OPEN 케어를 제재 이벤트 후 CANCELLED 전환

**전제**: Step 2(UserSanctionAppliedEvent)가 완료되어 이벤트 클래스가 존재해야 한다.

## 배경 정책

- SUSPENDED 요청자의 OPEN 케어: 상태 변경 없이 공개 조회에서 비노출. 해제 후 재노출 가능.
  - JPQL 쿼리(`findAllActiveRequests` 등)는 이미 `u.status = 'ACTIVE'` 필터로 처리됨.
  - native nearby 쿼리에는 누락 → 이 step에서 수정.
- BANNED 요청자의 OPEN 케어: 이벤트 후 CANCELLED 전환. DB row와 이력 유지.
- IN_PROGRESS 케어 (요청자 또는 제공자 제재): 자동 완료 차단. 관리자 검토 대상.
- 댓글/지원 생성: 제재 사용자 차단 (403).

## 변경 파일

### 1. `CareForbiddenException.java` — 팩토리 메서드 추가
경로: `backend/main/java/com/linkup/Petory/domain/care/exception/CareForbiddenException.java`

기존 팩토리 메서드 아래에 추가:
```java
public static CareForbiddenException sanctioned() {
    return new CareForbiddenException("제재된 사용자는 이 작업을 수행할 수 없습니다.");
}
```

### 2. `SpringDataJpaCareRequestRepository.java` — nearby 네이티브 쿼리 수정
경로: `backend/main/java/com/linkup/Petory/domain/care/repository/SpringDataJpaCareRequestRepository.java`

`findNearbyCareRequests` 쿼리에 사용자 상태 필터 추가. `cr.*`를 유지하면서 users 테이블 JOIN:

```sql
-- 기존
"SELECT cr.* FROM carerequest cr "
+ "WHERE cr.is_deleted = false "
+ "AND cr.latitude IS NOT NULL "
+ "AND cr.status IN ('OPEN', 'IN_PROGRESS') "
+ "AND cr.latitude BETWEEN (:lat - :radius / 111.0) AND (:lat + :radius / 111.0) "
...

-- 수정 (INNER JOIN users u 추가, u.status = 'ACTIVE' 조건 추가)
"SELECT cr.* FROM carerequest cr "
+ "INNER JOIN users u ON u.idx = cr.user_idx "
+ "WHERE cr.is_deleted = false "
+ "AND u.status = 'ACTIVE' "
+ "AND u.is_deleted = false "
+ "AND cr.latitude IS NOT NULL "
+ "AND cr.status IN ('OPEN', 'IN_PROGRESS') "
+ "AND cr.latitude BETWEEN (:lat - :radius / 111.0) AND (:lat + :radius / 111.0) "
...
```

### 3. `CareRequestService.java` — createCareRequest 제재 차단
경로: `backend/main/java/com/linkup/Petory/domain/care/service/CareRequestService.java`

케어 요청 생성 메서드에서 사용자 로드 직후 제재 검사 추가:
```java
// 사용자 로드 후 (기존 코드 어느 줄 이후든)
if (user.isSanctioned()) {
    throw CareForbiddenException.sanctioned();
}
```

`createCareRequest()` 내부에서 `usersRepository`로 사용자를 찾는 부분 직후에 삽입한다.

### 4. `CareRequestCommentService.java` — 댓글 생성 제재 차단
경로: `backend/main/java/com/linkup/Petory/domain/care/service/CareRequestCommentService.java`

댓글 생성 메서드(comment 작성 및 지원 신청 포함)에서 사용자 로드 직후 추가:
```java
if (user.isSanctioned()) {
    throw CareForbiddenException.sanctioned();
}
```

### 5. `CareRequestRepository.java` — OPEN 케어 조회 메서드 추가
경로: `backend/main/java/com/linkup/Petory/domain/care/repository/CareRequestRepository.java`

인터페이스에 메서드 추가:
```java
/** 이벤트 리스너용: 특정 사용자의 OPEN 케어 요청 목록 */
List<CareRequest> findOpenByUserId(Long userId);
```

### 6. `JpaCareRequestAdapter.java` — 구현 추가
경로: `backend/main/java/com/linkup/Petory/domain/care/repository/JpaCareRequestAdapter.java`

```java
@Override
public List<CareRequest> findOpenByUserId(Long userId) {
    return springRepository.findByUserIdxAndStatusAndIsDeletedFalse(userId, CareRequestStatus.OPEN);
}
```

### 7. `SpringDataJpaCareRequestRepository.java` — Spring Data JPA 메서드 추가
```java
List<CareRequest> findByUserIdxAndStatusAndIsDeletedFalse(
        @Param("userIdx") Long userIdx,
        @Param("status") CareRequestStatus status);
```

Spring Data JPA가 자동으로 쿼리를 생성한다. 단, `userIdx`는 `CareRequest.user.idx` 기준이므로 엔티티 필드명을 확인해야 한다. `CareRequest`의 user 필드 참조는 `user_idx`(컬럼) → `user.idx`(엔티티). Spring Data JPA 메서드명은 `findByUser_IdxAndStatusAndIsDeletedFalse`가 필요할 수 있다. 실제 엔티티 필드명을 확인 후 조정할 것.

### 8. `CareRequestScheduler.java` — 자동 완료 시 제재 체크
경로: `backend/main/java/com/linkup/Petory/domain/care/service/CareRequestScheduler.java`

`updateExpiredCareRequests()` 내 루프에서, `careRequestService.updateStatus(...)` 호출 전에 추가:

```java
// IN_PROGRESS 케어에서 요청자 제재 시 자동 완료 스킵
if (request.getStatus() == CareRequestStatus.IN_PROGRESS
        && request.getUser().isSanctioned()) {
    log.warn("자동 완료 스킵 (요청자 제재 중): careId={}, userId={}",
            request.getIdx(), request.getUser().getIdx());
    continue;
}
```

참고: 제공자(accepted CareApplication의 user) 제재 여부 체크는 별도 쿼리가 필요해 이 step에서는 요청자만 처리한다. 제공자 제재로 인한 보류는 관리자가 `confirmCareDeal` 차단(Step 6)과 함께 수동 검토한다.

### 9. `UserSanctionCareEventListener.java` — BANNED 사용자 OPEN 케어 취소
경로: `backend/main/java/com/linkup/Petory/domain/care/event/UserSanctionCareEventListener.java`

**신규 파일 생성**:
```java
package com.linkup.Petory.domain.care.event;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.event.UserSanctionAppliedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserSanctionCareEventListener {

    private final CareRequestRepository careRequestRepository;

    /**
     * BANNED 사용자의 OPEN 케어 요청을 CANCELLED로 전환한다.
     * SUSPENDED는 상태 변경 없이 조회 시점 필터로 처리하므로 이 리스너 대상에서 제외.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserSanctionApplied(UserSanctionAppliedEvent event) {
        if (event.status() != UserStatus.BANNED) {
            return;
        }
        try {
            List<CareRequest> openCares = careRequestRepository.findOpenByUserId(event.userId());
            for (CareRequest care : openCares) {
                care.setStatus(CareRequestStatus.CANCELLED);
                careRequestRepository.save(care);
                log.info("BANNED 사용자 OPEN 케어 취소: careId={}, userId={}",
                        care.getIdx(), event.userId());
            }
        } catch (Exception e) {
            log.error("BANNED 사용자 케어 취소 처리 실패 (관리자 수동 재처리 필요): userId={}, error={}",
                    event.userId(), e.getMessage(), e);
        }
    }
}
```

## 주의사항

- `CareRequest`에 `setStatus()` setter가 없으면 `Lombok @Setter`가 있는지 확인하거나 엔티티에 `updateStatus(status)` 메서드를 추가한다.
- 이벤트 리스너는 `AFTER_COMMIT`이므로 원래 트랜잭션과 별개. 실패해도 제재 자체는 롤백되지 않는다.
- `findOpenByUserId()` 구현에서 엔티티 필드명(`user.idx` vs `userIdx`)을 정확히 확인할 것.

## AC (Acceptance Criteria)

```bash
# 컴파일 통과
./gradlew compileJava

# 확인 포인트:
# - 제재 사용자가 케어 요청 생성 시 403
# - BANNED 사용자 제재 후 OPEN 케어가 CANCELLED로 전환
# - nearby 조회에서 SUSPENDED 사용자 케어 비노출
# - IN_PROGRESS 케어의 요청자가 제재 중이면 자동 완료 스킵
```
