# Notification 도메인 - 포트폴리오 상세 설명

## 1. 개요

Notification 도메인은 사용자 알림 시스템으로, 다양한 이벤트 발생 시 사용자에게 알림을 전송합니다. Server-Sent Events(SSE)를 통한 실시간 알림, Redis를 활용한 최신 알림 목록 관리, 읽은 알림 자동 정리 등의 기능을 제공합니다.

**주요 기능**:
- 알림 생성/조회/읽음 처리
- 실시간 알림 (SSE - Server-Sent Events)
- 알림 타입별 관리 (댓글, 펫케어, 실종제보 등)
- Redis를 활용한 최신 알림 목록 관리 (최대 50개, 24시간 TTL)
- Redis-DB 병합 조회 (중복 제거)
- 읽은 알림 자동 정리

---

## 2. 기능 설명

### 2.1 실시간 알림 (SSE)

**SSE 연결 프로세스**:
1. 클라이언트에서 SSE 연결 요청 (`GET /api/notifications/stream?userId={userId}`)
2. 서버에서 `SseEmitter` 생성 및 연결 저장
3. 연결 즉시 읽지 않은 알림 개수 전송
4. 이벤트 발생 시 실시간으로 알림 전송
5. 연결 종료 시 자동 정리

**알림 발송 프로세스**:
1. 이벤트 발생 (댓글 작성, 좋아요 등)
2. 알림 생성 및 DB 저장
3. Redis에 최신 알림 저장 (최대 50개, 24시간 TTL)
4. SSE 연결된 경우 실시간 알림 전송
5. 연결되지 않은 경우 DB/Redis에만 저장

### 2.2 알림 목록 조회

**조회 프로세스**:
1. Redis에서 최신 알림 목록 조회 시도
2. Redis에 데이터가 있으면 DB와 병합 (중복 제거)
3. Redis에 없으면 DB에서 조회
4. 최신순 정렬 (`createdAt` DESC)

### 2.3 읽음 처리

**읽음 처리 프로세스**:
1. 알림 클릭 또는 읽음 처리 요청
2. DB에서 `isRead` 필드 업데이트
3. Redis에서 해당 알림 제거
4. 모든 알림 읽음 처리 시 Redis에서 해당 사용자의 모든 알림 제거

---

## 3. 서비스 로직 설명

### 3.1 핵심 비즈니스 로직

#### 로직 1: 알림 생성 및 발송
**구현 위치**: `NotificationService.createNotification()`

```java
@Transactional
public NotificationDTO createNotification(Long userId, NotificationType type, String title, String content,
        Long relatedId, String relatedType) {
    // 1. 사용자 확인
    Users user = usersRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    
    // 2. 알림 생성
    Notification notification = Notification.builder()
            .user(user)
            .type(type)
            .title(title)
            .content(content)
            .relatedId(relatedId)
            .relatedType(relatedType)
            .isRead(false)
            .build();
    
    Notification saved = notificationRepository.save(notification);
    NotificationDTO dto = notificationConverter.toDTO(saved);
    
    // 3. Redis에 실시간 알림 저장 (최신 알림 목록 관리)
    saveToRedis(userId, dto);
    
    // 4. SSE를 통해 실시간 알림 전송 (연결된 경우)
    sseService.sendNotification(userId, dto);
    
    return dto;
}
```

**핵심 로직**:
- **알림 생성**: `Notification` 엔티티 생성 및 DB 저장
- **Redis 저장**: `saveToRedis()`로 최신 알림 목록 관리 (최대 50개, 24시간 TTL)
- **SSE 발송**: `sseService.sendNotification()`로 실시간 알림 전송 (연결된 경우)
- **트랜잭션 보장**: `@Transactional`로 알림 생성과 Redis 저장을 원자적으로 처리

#### 로직 2: Redis에 알림 저장
**구현 위치**: `NotificationService.saveToRedis()`

```java
private void saveToRedis(Long userId, NotificationDTO notification) {
    String redisKey = REDIS_KEY_PREFIX + userId;
    List<NotificationDTO> existingNotifications = getFromRedis(userId);
    
    // 수정 가능한 리스트 생성
    List<NotificationDTO> notifications;
    if (existingNotifications == null || existingNotifications.isEmpty()) {
        notifications = new ArrayList<>();
    } else {
        notifications = new ArrayList<>(existingNotifications);
    }
    
    // 최신 알림을 맨 앞에 추가 (최대 50개만 유지)
    notifications.add(0, notification);
    if (notifications.size() > 50) {
        notifications = notifications.subList(0, 50);
        notifications = new ArrayList<>(notifications);
    }
    
    notificationRedisTemplate.opsForValue().set(redisKey, notifications,
            Duration.ofHours(REDIS_TTL_HOURS));
}
```

**핵심 로직**:
- **최신 알림 우선**: 최신 알림을 맨 앞에 추가
- **최대 개수 제한**: 최대 50개만 유지
- **TTL 설정**: 24시간 후 자동 만료

#### 로직 3: 알림 목록 조회 (Redis-DB 병합)
**구현 위치**: `NotificationService.getUserNotifications()`

```java
public List<NotificationDTO> getUserNotifications(Long userId) {
    Users user = usersRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    
    // Redis에서 먼저 조회 시도
    List<NotificationDTO> redisNotifications = getFromRedis(userId);
    if (redisNotifications != null && !redisNotifications.isEmpty()) {
        // Redis에 데이터가 있으면 DB와 병합하여 반환
        List<NotificationDTO> dbNotifications = notificationRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(notificationConverter::toDTO)
                .collect(Collectors.toList());
        
        // Redis와 DB 데이터 병합 (중복 제거)
        return mergeNotifications(redisNotifications, dbNotifications);
    }
    
    // Redis에 없으면 DB에서 조회
    return notificationRepository.findByUserOrderByCreatedAtDesc(user)
            .stream()
            .map(notificationConverter::toDTO)
            .collect(Collectors.toList());
}
```

**핵심 로직**:
- **Redis 우선 조회**: Redis에서 최신 알림 목록 조회 시도
- **DB 병합**: Redis에 데이터가 있으면 DB와 병합하여 반환 (중복 제거)
- **최신순 정렬**: `createdAt` 기준 내림차순 정렬

#### 로직 4: Redis-DB 알림 병합 (중복 제거)
**구현 위치**: `NotificationService.mergeNotifications()`

**핵심 로직**:
- **중복 제거**: Redis 알림 ID를 Set으로 관리하여 중복 제거
- **병합**: Redis 알림 + Redis에 없는 DB 알림
- **정렬**: `createdAt` 기준 내림차순 정렬

#### 로직 5: SSE 연결 생성 및 관리
**구현 위치**: `NotificationSseService.createConnection()`

```java
public SseEmitter createConnection(Long userId) {
    SseEmitter emitter = new SseEmitter(3600000L); // 1시간 타임아웃
    
    emitter.onCompletion(() -> {
        log.info("SSE 연결 완료: userId={}", userId);
        emitters.remove(userId);
    });
    
    emitter.onTimeout(() -> {
        log.info("SSE 연결 타임아웃: userId={}", userId);
        emitters.remove(userId);
    });
    
    emitter.onError((ex) -> {
        log.error("SSE 연결 오류: userId={}, error={}", userId, ex.getMessage());
        emitters.remove(userId);
    });
    
    emitters.put(userId, emitter);
    return emitter;
}
```

**핵심 로직**:
- **연결 생성**: `SseEmitter` 생성 및 `ConcurrentHashMap`에 저장
- **타임아웃 설정**: 1시간 타임아웃
- **자동 정리**: `onCompletion`, `onTimeout`, `onError`로 연결 자동 정리
- **연결 관리**: 사용자별 SSE 연결 관리

#### 로직 6: 실시간 알림 전송
**구현 위치**: `NotificationSseService.sendNotification()`

**핵심 로직**:
- **연결 확인**: 사용자에게 SSE 연결이 있는지 확인
- **알림 전송**: `emitter.send()`로 실시간 알림 전송
- **에러 처리**: 전송 실패 시 연결 제거 및 에러 처리

#### 로직 7: 읽음 처리
**구현 위치**: `NotificationService.markAsRead()`

**핵심 로직**:
- **본인 확인**: 본인의 알림만 읽음 처리 가능
- **DB 업데이트**: `isRead` 필드를 `true`로 설정
- **Redis 제거**: Redis에서 해당 알림 제거

### 3.2 서비스 메서드 구조

#### NotificationService
| 메서드 | 설명 | 주요 로직 |
|--------|------|-----------|
| `createNotification()` | 알림 생성 및 발송 | 알림 생성, Redis 저장, SSE 전송 |
| `getUserNotifications()` | 사용자 알림 목록 조회 | Redis 우선 조회, DB 병합, 중복 제거 |
| `getUnreadNotifications()` | 읽지 않은 알림 목록 조회 | `findByUserAndIsReadFalseOrderByCreatedAtDesc()` |
| `getUnreadCount()` | 읽지 않은 알림 개수 조회 | `countUnreadByUser()` |
| `markAsRead()` | 알림 읽음 처리 | 본인 확인, DB 업데이트, Redis 제거 |
| `markAllAsRead()` | 모든 알림 읽음 처리 | 일괄 읽음 처리, Redis 전체 제거 |
| `saveToRedis()` | Redis에 알림 저장 | 최신 알림 추가, 최대 50개 유지, 24시간 TTL |
| `getFromRedis()` | Redis에서 알림 조회 | 사용자별 최신 알림 목록 조회 |
| `removeFromRedis()` | Redis에서 알림 제거 | 읽음 처리 시 Redis에서 제거 |
| `mergeNotifications()` | Redis-DB 알림 병합 | 중복 제거, 최신순 정렬 |

#### NotificationSseService
| 메서드 | 설명 | 주요 로직 |
|--------|------|-----------|
| `createConnection()` | SSE 연결 생성 | `SseEmitter` 생성, 연결 저장, 자동 정리 설정 |
| `sendNotification()` | 실시간 알림 전송 | 연결 확인, 알림 전송, 에러 처리 |
| `removeConnection()` | 연결 해제 | 연결 제거 및 완료 처리 |
| `getConnectedUserCount()` | 연결된 사용자 수 조회 | 현재 연결 수 반환 |

### 3.3 트랜잭션 처리
- **트랜잭션 범위**: 
  - 알림 생성: `@Transactional` - 알림 생성과 Redis 저장을 원자적으로 처리
  - 읽음 처리: `@Transactional` - DB 업데이트와 Redis 제거를 원자적으로 처리
  - 조회 메서드: `@Transactional(readOnly = true)` - 읽기 전용 최적화 (클래스 레벨)
- **격리 수준**: 기본값 (READ_COMMITTED)
- **Redis 작업**: 트랜잭션 외부에서 처리 (Redis는 트랜잭션 미지원)

---

## 4. 아키텍처 설명

### 4.1 엔티티 구조

#### Notification (알림)
```java
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;
    
    @ManyToOne
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user; // 알림을 받을 사용자
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type; // 알림 타입
    
    @Column(nullable = false)
    private String title; // 알림 제목
    
    @Column(length = 500)
    private String content; // 알림 내용
    
    @Column(name = "related_id")
    private Long relatedId; // 관련 게시글/댓글 ID
    
    @Column(name = "related_type")
    private String relatedType; // 관련 타입 (BOARD, CARE_REQUEST, MISSING_PET 등)
    
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false; // 읽음 여부
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
```

**특징**:
- `BaseTimeEntity`를 상속하지 않음 (`@PrePersist`로 직접 `createdAt` 관리)
- 읽음 여부: `isRead` 필드로 읽음 상태 관리
- 관련 엔티티: `relatedId`, `relatedType`으로 다양한 엔티티와 연동

#### NotificationType (알림 타입)
```java
public enum NotificationType {
    CARE_REQUEST_COMMENT,    // 펫케어 요청글 댓글
    BOARD_COMMENT,           // 커뮤니티 게시글 댓글
    MISSING_PET_COMMENT      // 실종 제보 게시글 댓글
}
```

### 4.2 도메인 구조
```
domain/notification/
  ├── controller/
  │   └── NotificationController.java
  ├── service/
  │   ├── NotificationService.java
  │   └── NotificationSseService.java
  ├── entity/
  │   ├── Notification.java
  │   └── NotificationType.java (enum)
  ├── repository/
  │   └── NotificationRepository.java
  ├── converter/
  │   └── NotificationConverter.java
  └── dto/
      └── NotificationDTO.java
```

### 4.3 엔티티 관계도 (ERD)
```mermaid
erDiagram
    Users ||--o{ Notification : "수신"
```

### 4.4 API 설계

#### REST API
| 엔드포인트 | Method | 설명 |
|-----------|--------|------|
| `/api/notifications` | GET | 사용자 알림 목록 조회 (userId 파라미터, 인증 필요) |
| `/api/notifications/unread` | GET | 읽지 않은 알림 목록 조회 (userId 파라미터, 인증 필요) |
| `/api/notifications/unread/count` | GET | 읽지 않은 알림 개수 조회 (userId 파라미터, 인증 필요) |
| `/api/notifications/{notificationId}/read` | PUT | 알림 읽음 처리 (userId 파라미터, 인증 필요) |
| `/api/notifications/read-all` | PUT | 모든 알림 읽음 처리 (userId 파라미터, 인증 필요) |

#### SSE API
| 엔드포인트 | Method | 설명 |
|-----------|--------|------|
| `/api/notifications/stream` | GET | SSE 연결 생성 (userId 파라미터, `text/event-stream`) |

**알림 목록 조회 요청 예시**:
```http
GET /api/notifications?userId=1
```

**알림 목록 조회 응답 예시**:
```json
[
  {
    "idx": 1,
    "userId": 1,
    "type": "BOARD_COMMENT",
    "title": "게시글에 새로운 댓글이 달렸습니다",
    "content": "홍길동님이 댓글을 남겼습니다: 좋은 글 감사합니다.",
    "relatedId": 123,
    "relatedType": "BOARD",
    "isRead": false,
    "createdAt": "2024-01-15T14:00:00"
  }
]
```

**읽지 않은 알림 개수 조회 요청 예시**:
```http
GET /api/notifications/unread/count?userId=1
```

**읽지 않은 알림 개수 조회 응답 예시**:
```json
5
```

**SSE 연결 요청 예시**:
```http
GET /api/notifications/stream?userId=1
Accept: text/event-stream
```

**SSE 이벤트 형식**:
```
event: unreadCount
data: 5

event: notification
data: {"idx":1,"userId":1,"type":"BOARD_COMMENT","title":"게시글에 새로운 댓글이 달렸습니다","content":"홍길동님이 댓글을 남겼습니다: 좋은 글 감사합니다.","relatedId":123,"relatedType":"BOARD","isRead":false,"createdAt":"2024-01-15T14:00:00"}
```

---

## 5. 트랜잭션 처리

### 5.1 트랜잭션 전략
- **알림 생성**: `@Transactional` - 알림 생성과 Redis 저장을 원자적으로 처리 (Redis는 트랜잭션 외부)
- **읽음 처리**: `@Transactional` - DB 업데이트와 Redis 제거를 원자적으로 처리 (Redis는 트랜잭션 외부)
- **조회 메서드**: `@Transactional(readOnly = true)` - 읽기 전용 최적화 (클래스 레벨)

### 5.2 동시성 제어
- **SSE 연결 관리**: `ConcurrentHashMap`으로 동시성 안전하게 연결 관리
- **Redis 작업**: Redis는 단일 스레드이므로 동시성 문제 없음
- **읽음 처리**: 본인 확인으로 권한 체크

---

## 6. 트러블슈팅

---

## 7. 성능 최적화

### 7.1 DB 최적화

#### 인덱스 전략
```sql
-- 사용자별 알림 조회
CREATE INDEX idx_notification_user_created 
ON notifications(user_idx, created_at DESC);

-- 읽지 않은 알림 조회
CREATE INDEX idx_notification_user_unread 
ON notifications(user_idx, is_read, created_at DESC);
```

**선정 이유**:
- 자주 조회되는 컬럼 조합 (`user_idx`, `created_at`)
- WHERE 절에서 자주 사용되는 조건 (`user_idx`, `is_read`)
- 최신순 정렬을 위한 인덱스 (`created_at DESC`)

### 7.2 애플리케이션 레벨 최적화

#### Redis 활용
**구현 위치**: `NotificationService.saveToRedis()`, `getFromRedis()`

**최적화 사항**:
- **최신 알림 캐싱**: 최신 알림 50개를 Redis에 저장하여 빠른 조회
- **TTL 설정**: 24시간 후 자동 만료로 메모리 관리
- **Redis-DB 병합**: Redis와 DB 데이터를 병합하여 완전한 알림 목록 제공

**효과**: 최신 알림 조회 시 DB 쿼리 감소, 응답 시간 단축

#### SSE 연결 관리
**구현 위치**: `NotificationSseService`

**최적화 사항**:
- **타임아웃 설정**: 1시간 타임아웃으로 연결 누수 방지
- **자동 정리**: `onCompletion`, `onTimeout`, `onError`로 연결 자동 정리
- **에러 처리**: 전송 실패 시 연결 제거 및 에러 처리

**효과**: 연결 누수 방지, 메모리 효율적 관리

#### 중복 제거 최적화
**구현 위치**: `NotificationService.mergeNotifications()`

**최적화 사항**:
- **Set 활용**: Redis 알림 ID를 Set으로 관리하여 O(1) 중복 체크
- **스트림 처리**: Java Stream API로 효율적인 병합 처리

---

## 8. 핵심 포인트 요약

### 8.1 실시간 알림 (SSE)
- **Server-Sent Events**: 단방향 실시간 통신으로 알림 전송
- **연결 관리**: `ConcurrentHashMap`으로 사용자별 SSE 연결 관리
- **타임아웃 설정**: 1시간 타임아웃으로 연결 누수 방지
- **자동 정리**: 연결 종료 시 자동으로 정리

### 8.2 Redis 활용
- **최신 알림 캐싱**: 최신 알림 50개를 Redis에 저장 (24시간 TTL)
- **Redis-DB 병합**: Redis와 DB 데이터를 병합하여 완전한 알림 목록 제공
- **중복 제거**: Set을 활용한 효율적인 중복 제거
- **읽음 처리**: 읽음 처리 시 Redis에서도 제거

### 8.3 알림 타입 관리
- **타입별 관리**: `NotificationType` enum으로 알림 타입 관리
- **관련 엔티티 연동**: `relatedId`, `relatedType`으로 다양한 엔티티와 연동
- **확장 가능**: 새로운 알림 타입 추가 시 enum만 추가하면 됨

### 8.4 성능 최적화
- **Redis 캐싱**: 최신 알림 조회 시 DB 쿼리 감소
- **인덱스 전략**: 사용자별, 읽음 여부별 인덱스로 조회 성능 향상
- **중복 제거**: Set을 활용한 효율적인 중복 제거

### 8.5 엔티티 설계 특징
- **BaseTimeEntity 미사용**: `@PrePersist`로 직접 `createdAt` 관리
- **읽음 여부**: `isRead` 필드로 읽음 상태 관리
- **관련 엔티티**: `relatedId`, `relatedType`으로 다양한 엔티티와 연동
- **타입 관리**: `NotificationType` enum으로 알림 타입 관리

### 8.6 SSE 연결 관리
- **연결 저장**: `ConcurrentHashMap<Long, SseEmitter>`로 사용자별 연결 저장
- **자동 정리**: `onCompletion`, `onTimeout`, `onError`로 연결 자동 정리
- **에러 처리**: 전송 실패 시 연결 제거 및 에러 처리
- **초기 알림 개수**: 연결 즉시 읽지 않은 알림 개수 전송
