# Notification 읽음 처리 성능 리팩토링

## 1. 목적

`NotificationService`의 조회·읽음 처리 경로에서 발생하는 불필요한 사용자 조회와 행별 UPDATE를 제거한다.

이번 작업은 다음 순서로 검증한다.

1. 현재 구현의 Repository 호출 구조를 테스트로 고정한다.
2. 현재 구현의 예상 SQL 수를 기준선으로 기록한다.
3. JPQL bulk UPDATE와 `userId` 직접 조회로 리팩토링한다.
4. 동일한 기능 테스트를 개선된 호출 구조에 맞춰 다시 실행한다.
5. 전후 결과를 이 문서와 관련 도메인·인터뷰 문서에 반영한다.

---

## 2. 현재 구현

대상:

- `NotificationService.markAllAsRead()`
- `NotificationService.getUserNotifications()`
- `NotificationService.getUnreadNotifications()`
- `NotificationService.getUnreadCount()`

### 2.1 전체 읽음 처리

현재 `markAllAsRead()`는 사용자와 모든 미읽음 알림 엔티티를 조회한 뒤 각 엔티티의 상태를 변경한다.

```java
Users user = usersRepository.findById(userId)
        .orElseThrow(UserNotFoundException::new);

List<Notification> unreadNotifications = notificationRepository
        .findByUserAndIsReadFalseOrderByCreatedAtDesc(user);

unreadNotifications.forEach(notification -> notification.setIsRead(true));
notificationRepository.saveAll(unreadNotifications);
```

트랜잭션 안에서 조회한 `Notification`은 관리 상태다. 따라서 `setIsRead(true)` 후 별도의 `saveAll()`을 호출하지 않아도 커밋 시 변경 감지가 실행된다.

하지만 변경 감지는 엔티티마다 UPDATE를 생성한다. `saveAll()` 한 번이 SQL UPDATE 한 번을 의미하지 않는다.

미읽음 알림이 `N`개일 때 예상 SQL 구조:

```text
Users SELECT               1
Unread Notification SELECT 1
Notification UPDATE        N
--------------------------------
합계                        N + 2
```

알림 100개 기준 예상:

```text
SELECT 2번 + UPDATE 100번 = 102 statements
```

이 문제는 조회 과정의 전형적인 N+1보다 **일괄 변경을 엔티티 단위로 처리해서 발생하는 row-by-row N번 UPDATE**라고 표현하는 것이 정확하다.

### 2.2 불필요한 Users 조회

다음 메서드는 `Users` 엔티티 자체를 사용하지 않고 알림 검색 조건으로만 전달한다.

| 메서드 | 현재 추가 조회 | 실제 필요한 값 |
|---|---:|---|
| `getUserNotifications()` | `Users SELECT` 1번 | `userId` |
| `getUnreadNotifications()` | `Users SELECT` 1번 | `userId` |
| `getUnreadCount()` | `Users SELECT` 1번 | `userId` |
| `markAllAsRead()` | `Users SELECT` 1번 | `userId` |

`markAsRead()`는 현재 `Users`를 따로 조회하지 않으므로 대상이 아니다.

REST API는 `AuthenticatedUserIdResolver`가 인증 객체의 `CustomUserDetails.idx`를 전달한다. 정상 인증 요청에서는 이미 활성 사용자 확인이 끝난 상태이므로, 알림 조회마다 같은 사용자를 다시 조회할 실익이 낮다.

서비스를 직접 호출하면서 존재하지 않는 `userId`를 전달하는 경우에는 동작이 달라질 수 있다.

```text
현재: UserNotFoundException
개선 후 조회: 빈 목록 또는 0
개선 후 bulk UPDATE: 영향받은 행 0
```

이는 인증된 현재 사용자 ID만 받는 API 계약을 기준으로 허용한다.

---

## 3. 해결 방안

### 3.1 JPQL bulk UPDATE

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
    UPDATE Notification n
       SET n.isRead = true
     WHERE n.user.idx = :userId
       AND n.isRead = false
""")
int markAllAsReadByUserId(@Param("userId") Long userId);
```

예상 SQL:

```sql
UPDATE notifications
   SET is_read = true
 WHERE user_idx = ?
   AND is_read = false;
```

알림 개수와 관계없이 UPDATE 한 번으로 처리한다.

```text
Before: N + 2 statements
After:  1 UPDATE statement
```

bulk UPDATE는 영속성 컨텍스트를 우회한다. 해당 트랜잭션에서 이미 로딩된 `Notification`이 있을 수 있으므로 `clearAutomatically = true`로 오래된 관리 엔티티가 남지 않도록 한다.

### 3.2 userId 직접 조회

Repository 계약을 `Users` 엔티티 대신 숫자 ID 중심으로 변경한다.

```java
List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);

long countUnreadByUserId(Long userId);
```

목록과 count 요청에서 사용자 조회 한 번을 제거한다.

### 3.3 Redis 처리

DB bulk UPDATE 후 기존 정책대로 Redis 사용자 키를 삭제한다.

```text
notification:{userId}
```

현재 Redis 삭제는 DB 커밋 전에 실행된다. Redis 삭제 성공 후 DB 커밋이 실패하면 일시적 불일치가 생길 수 있지만, 캐시 미스 시 DB에서 다시 조회하는 구조이므로 이번 성능 리팩토링에서는 기존 정책을 유지한다. 커밋 이후 삭제는 별도 일관성 개선 과제로 분리한다.

---

## 4. 테스트 전략

### 4.1 기준선 테스트

`NotificationServiceReadPerformanceTest`에서 미읽음 알림 100개를 반환하도록 구성하고 현재 호출 구조를 검증한다.

검증 항목:

- 사용자 조회 1회
- 미읽음 목록 조회 1회
- `saveAll()` 1회
- 100개 엔티티의 `isRead=true`
- Redis 사용자 키 삭제

이 테스트는 실제 SQL을 실행하지 않지만, 현재 서비스가 엔티티 100개를 로딩하고 행별 변경 감지 경로를 사용한다는 구조적 증거다.

실제 prepared statement 수는 MySQL 통합 테스트와 Hibernate Statistics가 필요하다. 로컬 DB를 사용할 수 있을 때 별도 통합 측정을 수행한다.

### 4.2 개선 후 테스트

동일한 기능 요구사항을 다음 기준으로 검증한다.

- `markAllAsReadByUserId(userId)` 1회
- `Users` 조회 없음
- unread 목록 조회 없음
- `saveAll()` 없음
- Redis 사용자 키 삭제

조회 메서드는 다음을 검증한다.

- 사용자 ID 기반 Repository 호출
- `Users` 조회 없음
- 기존 DTO 결과 유지

---

## 5. 측정 결과

### 5.1 리팩토링 전

실행 명령:

```bash
./gradlew test \
  --tests com.linkup.Petory.domain.notification.service.NotificationServiceReadPerformanceTest
```

결과:

```text
NotificationServiceReadPerformanceTest
> 기준선: 전체 읽음은 사용자와 미읽음 100개를 조회한 뒤 saveAll 경로를 사용한다 PASSED

BUILD SUCCESSFUL in 8s
```

검증된 호출 구조:

```text
usersRepository.findById(userId)                              1회
notificationRepository.findByUserAndIsReadFalse...(user)     1회
notificationRepository.saveAll(100개 엔티티)                  1회
Redis key delete                                               1회
```

이 테스트는 Mockito 기반 구조 테스트로 관리 엔티티 100개의 상태를 변경하는 경로를 고정했다.

추가로 `NotificationReadQueryPerformanceTest`에서 MySQL과 Hibernate Statistics를 사용해 기존 알고리즘을 재현했다.

```bash
./gradlew test \
  --tests com.linkup.Petory.domain.notification.repository.NotificationReadQueryPerformanceTest
```

실측 결과:

```text
Users SELECT               1
Unread Notification SELECT 1
Notification UPDATE      100
--------------------------------
prepare statements       102
```

### 5.2 리팩토링 후

동일 테스트 클래스의 기대 조건을 개선 구조에 맞춰 변경하고 다시 실행했다.

실행 명령:

```bash
./gradlew test \
  --tests com.linkup.Petory.domain.notification.service.NotificationServiceReadPerformanceTest
```

결과:

```text
NotificationServiceReadPerformanceTest
> 개선 후: 미읽음 개수는 Users 조회 없이 userId로 직접 COUNT한다 PASSED
> 개선 후: 미읽음 목록은 Users 조회 없이 userId로 직접 조회한다 PASSED
> 개선 후: 전체 읽음은 알림 개수와 무관하게 bulk UPDATE를 한 번 호출한다 PASSED
> 개선 후: 전체 목록은 Users 조회 없이 userId로 직접 조회한다 PASSED

BUILD SUCCESSFUL in 3s
```

검증된 호출 구조:

```text
notificationRepository.markAllAsReadByUserId(userId) 1회
usersRepository.findById(userId)                      0회
미읽음 Notification 목록 조회                         0회
saveAll                                               0회
Redis key delete                                      1회
```

같은 통합 테스트에서 100개 알림을 다시 미읽음 상태로 만든 뒤 실제 Repository의 `markAllAsReadByUserId()`를 실행했다.

실측 결과:

```text
affected rows              100
prepare statements           1
```

JPQL bulk UPDATE는 알림 개수와 관계없이 DB에 UPDATE statement 하나를 전달하는 것이 Hibernate Statistics로 확인됐다.

### 5.3 비교

| 항목 | Before | After |
|---|---:|---:|
| 전체 읽음 사용자 조회 | 1 | 0 |
| 미읽음 목록 조회 | 1 | 0 |
| 알림 UPDATE | N | 1 |
| 100개 기준 예상 statement | 102 | 1 |
| 알림 엔티티 메모리 로딩 | 100 | 0 |

100개 기준 statement 감소:

```text
102 → 1
약 99.0% 감소
```

목록·미읽음 목록·미읽음 count 경로도 각각 사용자 조회 1회가 제거됐다.

### 5.4 검증 범위

- `NotificationServiceReadPerformanceTest`
  - 서비스가 bulk UPDATE Repository를 한 번만 호출하는지 검증
  - 목록/count 경로에서 `Users` 조회가 제거됐는지 검증
- `NotificationReadQueryPerformanceTest`
  - MySQL 임시 테이블에 알림 100개 적재
  - 기존 행별 변경 알고리즘 `102 statements` 실측
  - 실제 JPQL bulk UPDATE `1 statement` 실측
- `PetoryApplicationTests`
  - Spring Context 기동 성공
  - 신규 JPQL Repository 쿼리 파싱 성공
- `./gradlew compileJava`
  - Repository 계약과 서비스 변경 컴파일 성공

통합 테스트는 기존 로컬 `notifications` 데이터를 건드리지 않도록 같은 DB 연결 안에서 `CREATE TEMPORARY TABLE notifications`를 사용한다.

### 5.5 추가 발견: notifications.updated_at 스키마 불일치

첫 통합 테스트에서 실제 `notifications` 테이블에 `updated_at`이 없어 엔티티 INSERT가 실패했다.

원인:

```text
Entity table: notifications
기존 catch-up migration: ALTER TABLE notification
```

`remaining-domains-updated-at-catchup.sql`의 단수형 테이블명 오타를 `notifications`로 수정했고, 이미 나머지 catch-up SQL을 적용한 DB를 위한 독립 보정 스크립트를 추가했다.

```text
backend/main/resources/sql/migration/notifications-add-updated-at-column.sql
```

이 스크립트는 코드에 포함했지만 현재 로컬 DB에는 자동 적용하지 않았다.

---

## 6. 관련 문서

다음 문서를 동기화했다.

- `docs/domains/notification.md`
- `docs/architecture/notification/알림 시스템 아키텍처.md`
- `docs/architecture/user/알림 시스템 아키텍처.md`
- `docs/interview/concepts/04_JPA_N+1.md`
