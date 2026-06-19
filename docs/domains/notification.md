# Notification 도메인

> 기준: 현재 `domain/notification`, SSE 연결, Redis 알림 캐시, FCM 토큰/푸시, 프론트 `Navigation` 코드.  
> 역할: 도메인 이벤트를 사용자 알림으로 저장하고, 앱이 열려 있으면 SSE로, 네이티브 앱에서는 FCM으로 전달한다.

---

## 1. 도메인 책임

Notification 도메인은 Petory의 공통 알림 파이프라인이다.

- 알림 영구 저장: MySQL `notifications`
- 최신 알림 캐시: Redis `notification:{userId}`
- 실시간 전달: SSE `GET /api/notifications/stream`
- 모바일 푸시: FCM token 저장 후 Firebase Admin SDK 발송
- 읽음 처리: 단건/전체 읽음 처리와 Redis 캐시 정리

현재 알림을 직접 생성하는 도메인은 다음이다.

| 발생 도메인 | NotificationType | relatedType |
|---|---|---|
| Board 댓글 | `BOARD_COMMENT` | `BOARD` |
| Care 댓글 | `CARE_REQUEST_COMMENT` | `CARE_REQUEST` |
| MissingPet 댓글 | `MISSING_PET_COMMENT` | `MISSING_PET` |
| Recommendation 건강 경고 | `PET_HEALTH_ALERT` | `PET_INTENT_SIGNAL` |

---

## 2. 주요 코드 위치

| 영역 | 파일 |
|---|---|
| 알림 REST/SSE API | `domain/notification/controller/NotificationController.java` |
| FCM 토큰 API | `domain/notification/controller/FcmTokenController.java` |
| 알림 생성/조회/읽음 서비스 | `domain/notification/service/NotificationService.java` |
| SSE 연결 서비스 | `domain/notification/service/NotificationSseService.java` |
| FCM 발송 서비스 | `domain/notification/service/FcmService.java` |
| 알림 엔티티 | `domain/notification/entity/Notification.java` |
| FCM 토큰 엔티티 | `domain/notification/entity/FcmToken.java` |
| 알림 타입 enum | `domain/notification/entity/NotificationType.java` |
| Redis 설정 | `global/security/RedisConfig.java` |
| Firebase 설정 | `global/config/FirebaseConfig.java` |
| JWT query token 처리 | `filter/JwtAuthenticationFilter.java` |
| 프론트 알림 API | `frontend/src/api/notificationApi.js` |
| 프론트 FCM 초기화 | `frontend/src/api/pushNotifications.js` |
| 프론트 알림 UI/SSE | `frontend/src/components/Layout/Navigation.js` |

---

## 3. API

### 3.1 알림함 API

모든 API는 현재 로그인 사용자 기준이다. 클라이언트가 `userId`를 넘기지 않는다.

```http
GET /api/notifications
GET /api/notifications/unread
GET /api/notifications/unread/count
PUT /api/notifications/{notificationId}/read
PUT /api/notifications/read-all
```

권한:

- `@PreAuthorize("isAuthenticated()")`
- `/api/**` catch-all 인증 규칙 적용
- 사용자 식별은 `AuthenticatedUserIdResolver.requireCurrentUserIdx()`

### 3.2 SSE 스트림

```http
GET /api/notifications/stream?token={JWT}
Accept: text/event-stream
```

EventSource는 일반적으로 `Authorization` 헤더를 붙이기 어렵기 때문에 프론트는 query string으로 토큰을 넘긴다.

`JwtAuthenticationFilter`는 다음 순서로 JWT를 찾는다.

1. `Authorization: Bearer ...`
2. query parameter `token`

SSE 이벤트:

| event | data |
|---|---|
| `unreadCount` | 연결 직후 전송되는 읽지 않은 알림 개수 문자열 |
| `notification` | 새 `NotificationDTO` JSON |

### 3.3 FCM 토큰 API

```http
POST   /api/fcm/token
DELETE /api/fcm/token
```

요청 body:

```json
{
  "token": "fcm-device-token",
  "deviceType": "ANDROID"
}
```

`deviceType`은 `ANDROID`, `IOS`만 허용한다.

프론트 `pushNotifications.js`는 Capacitor 네이티브 앱에서만 동작한다. 웹 환경에서는 no-op이다.

---

## 4. 데이터 모델

### 4.1 Notification

현재 엔티티 테이블명은 `notifications`다.

| 필드 | 설명 |
|---|---|
| `idx` | 알림 PK |
| `user` | 알림 수신자 |
| `type` | `NotificationType` |
| `title` | 알림 제목 |
| `content` | 알림 내용 |
| `relatedId` | 이동 또는 참조 대상 ID |
| `relatedType` | 참조 대상 타입 문자열 |
| `isRead` | 읽음 여부 |
| `createdAt`, `updatedAt` | `BaseTimeEntity`에서 관리 |

주의: 일부 오래된 migration/ERD 문서는 `notification` 단수 테이블명을 사용한다. 현재 코드 기준은 `@Table(name = "notifications")`다.

### 4.2 NotificationType

```java
CARE_REQUEST_COMMENT
BOARD_COMMENT
MISSING_PET_COMMENT
PET_HEALTH_ALERT
```

### 4.3 FcmToken

테이블명은 `fcm_token`이다.

| 필드 | 설명 |
|---|---|
| `idx` | 토큰 PK |
| `user` | 토큰 소유 사용자 |
| `token` | FCM device token. 전체 unique |
| `deviceType` | `ANDROID` 또는 `IOS` |
| `updatedAt` | Hibernate `@UpdateTimestamp` |

`saveToken()`은 같은 token이 다른 사용자에게 이미 묶여 있으면 기존 row를 삭제하고 새 사용자에게 귀속한다.

---

## 5. 알림 생성 흐름

`NotificationService.createNotification()`은 하나의 진입점이다.

흐름:

1. 수신자 `Users` 조회
2. `Notification` 생성
3. MySQL 저장
4. `NotificationDTO` 변환
5. Redis `notification:{userId}`에 최신 알림 목록 저장
6. SSE 연결이 있으면 `notification` event 발송
7. Firebase가 초기화되어 있으면 사용자 기기 토큰들로 FCM 발송

Redis 저장 정책:

| 항목 | 값 |
|---|---|
| key | `notification:{userId}` |
| value | `List<NotificationDTO>` |
| 최대 개수 | 50개 |
| TTL | 24시간 |
| serializer | JSON serializer |

DB 저장은 트랜잭션 안에서 처리되지만 Redis/SSE/FCM은 DB 트랜잭션과 원자적으로 묶이지 않는다.

---

## 6. 조회와 읽음 처리

### 6.1 목록 조회

`getUserNotifications()`는 Redis를 먼저 확인한다.

1. Redis에 최신 알림 목록이 있으면 DB 전체 알림을 함께 조회
2. Redis 알림 ID 기준으로 DB 중복 제거
3. `createdAt DESC` 정렬
4. Redis가 비어 있으면 DB 목록만 반환

### 6.2 읽지 않은 알림

`getUnreadNotifications()`는 DB에서 `isRead=false` 목록을 조회한다.

`getUnreadCount()`는 SSE 연결 초기 전송에도 쓰이기 때문에 `@Transactional(propagation = NOT_SUPPORTED)`로 실행된다.

### 6.3 읽음 처리

단건 읽음:

1. 알림 row 조회
2. 알림 수신자가 현재 사용자와 같은지 검증
3. `isRead=true`
4. Redis 목록에서 해당 알림 제거

전체 읽음:

1. 현재 사용자의 unread 알림 목록 조회
2. 모두 `isRead=true`
3. Redis `notification:{userId}` 삭제

---

## 7. SSE 연결 관리

`NotificationSseService`는 서버 메모리의 `ConcurrentHashMap<Long, SseEmitter>`로 연결을 관리한다.

| 항목 | 현재 구현 |
|---|---|
| timeout | 1시간 |
| key | `userId` |
| 연결 수 | 사용자당 emitter 1개 |
| 정리 시점 | completion, timeout, error |
| 전송 실패 | emitter 제거 후 `completeWithError()` |

동일 사용자가 여러 탭/기기에서 SSE를 열면 마지막 emitter가 map에 저장된다. 멀티 탭 동시 수신 보장은 현재 구조의 목표가 아니다.

프론트 `Navigation`은 SSE 연결 실패 시 5분 간격으로 unread count를 다시 조회하는 fallback polling을 시작한다.

---

## 8. FCM 푸시

`FirebaseConfig`는 `firebase.service-account.path`가 없으면 Firebase 초기화를 건너뛴다. 이 경우 FCM 발송은 조용히 생략된다.

`FcmService.sendToUser()` 흐름:

1. FirebaseApp 초기화 여부 확인
2. 사용자 조회
3. 사용자 FCM token 목록 조회
4. 각 token에 Firebase message 발송
5. 다음 오류는 토큰 삭제
   - `UNREGISTERED`
   - `INVALID_ARGUMENT`
   - `SENDER_ID_MISMATCH`

현재 FCM message에는 title/body notification payload만 담는다. relatedId, relatedType 같은 data payload는 넣지 않는다.

---

## 9. 프론트 연동

`Navigation.js`가 알림 UI의 중심이다.

- 로그인 사용자가 있으면 SSE 연결
- 연결 직후 unread count 요청
- `notification` event 수신 시 목록 앞에 추가하고 unread count 증가
- 알림 드롭다운을 열면 목록과 unread count를 다시 조회
- 알림 클릭 시 읽음 처리 후 관련 화면으로 이동

이동 처리:

| 조건 | 동작 |
|---|---|
| `relatedType === "BOARD"` | 커뮤니티 탭 + `openBoardDetail` event |
| `relatedType === "MISSING_PET"` | 실종 제보 탭 + `openMissingPetDetail` event |
| `relatedType === "CARE_REQUEST"` | 탐색 탭으로 이동 |
| `type === "PET_HEALTH_ALERT"` | 탐색 탭 + 동물병원 그룹 이동 event |

---

## 10. 예외와 장애 처리

| 상황 | 처리 |
|---|---|
| 수신자 사용자 없음 | `UserNotFoundException` |
| 알림 없음 | `NotificationNotFoundException` |
| 다른 사용자의 알림 읽음 처리 | `NotificationForbiddenException.ownNotificationOnly()` |
| SSE 초기 unread count 전송 실패 | 로그만 남기고 stream 반환 |
| SSE notification 전송 실패 | emitter 제거 |
| Firebase 설정 없음 | FCM 생략 |
| FCM invalid token | token row 삭제 |

---

## 11. 현재 한계와 주의사항

- SSE 연결은 서버 메모리 기반이라 다중 인스턴스 확장 시 사용자 연결 라우팅 문제가 생긴다.
- 사용자당 SSE emitter 1개만 유지한다.
- Redis/SSE/FCM 발송은 DB 트랜잭션과 원자적으로 묶이지 않는다.
- query string JWT는 로그/브라우저 히스토리 노출 위험이 있어 운영에서는 별도 보완이 필요하다.
- Redis 캐시는 최신 50개/24시간 용도이며 영구 저장소는 MySQL이다.
- FCM payload에 이동용 data가 없어 네이티브 알림 탭 후 상세 라우팅은 제한적이다.
- 일부 오래된 문서와 migration은 `notification` 단수 테이블명을 쓰지만 현재 엔티티는 `notifications`다.

---

## 12. DomainV2 페이지에 넣을 포인트

- 알림은 MySQL 영구 저장, Redis 최신 캐시, SSE 실시간 전송, FCM 모바일 푸시로 나뉜다.
- `createNotification()` 하나로 도메인 이벤트를 공통 알림 파이프라인에 태운다.
- EventSource 제약 때문에 SSE는 query token 인증을 지원한다.
- Redis는 최신 50개만 24시간 유지하고, 목록 조회 시 DB와 병합해 정합성을 보완한다.
- 읽음 처리는 DB 상태 변경과 Redis 캐시 제거를 같이 수행한다.
- 추천 도메인의 `MEDICAL + HIGH` signal은 `PET_HEALTH_ALERT`로 연결된다.
- 현재 구조의 핵심 한계는 서버 메모리 기반 SSE 연결과 트랜잭션 밖 실시간/푸시 발송이다.
