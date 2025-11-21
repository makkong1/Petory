# 알림 시스템 전략 정리

## 📋 개요

Petory 프로젝트에서 댓글 작성 시 자동으로 알림을 발송하는 시스템을 구현했습니다. Redis를 활용하여 실시간 알림 관리와 성능 최적화를 제공합니다.

## 🎯 구현된 알림 타입

### 1. 펫케어 요청글 댓글 알림 (`CARE_REQUEST_COMMENT`)

**발송 조건**:
- SERVICE_PROVIDER가 펫케어 요청글에 댓글 작성 시
- 댓글 작성자 ≠ 요청글 작성자인 경우에만 발송 (자기 자신에게 알림 방지)

**발송 위치**: `CareRequestCommentService.addComment()`

**알림 내용**:
- 제목: "펫케어 요청글에 새로운 댓글이 달렸습니다"
- 내용: "{작성자명}님이 댓글을 남겼습니다: {댓글 내용 (50자 제한)}"
- 관련 ID: CareRequest ID
- 관련 타입: "CARE_REQUEST"

**코드 예시**:
```java
// 알림 발송: 댓글 작성자가 게시글 작성자가 아닌 경우에만 알림 발송
Long requestOwnerId = careRequest.getUser().getIdx();
if (!requestOwnerId.equals(user.getIdx())) {
    notificationService.createNotification(
        requestOwnerId,
        NotificationType.CARE_REQUEST_COMMENT,
        "펫케어 요청글에 새로운 댓글이 달렸습니다",
        String.format("%s님이 댓글을 남겼습니다: %s", user.getUsername(), 
            dto.getContent().length() > 50 ? dto.getContent().substring(0, 50) + "..." : dto.getContent()),
        careRequest.getIdx(),
        "CARE_REQUEST");
}
```

---

### 2. 커뮤니티 게시글 댓글 알림 (`BOARD_COMMENT`)

**발송 조건**:
- 누구나 커뮤니티 게시글에 댓글 작성 시
- 댓글 작성자 ≠ 게시글 작성자인 경우에만 발송 (자기 자신에게 알림 방지)

**발송 위치**: `CommentService.addComment()`

**알림 내용**:
- 제목: "내 게시글에 새로운 댓글이 달렸습니다"
- 내용: "{작성자명}님이 댓글을 남겼습니다: {댓글 내용 (50자 제한)}"
- 관련 ID: Board ID
- 관련 타입: "BOARD"

**코드 예시**:
```java
// 알림 발송: 댓글 작성자가 게시글 작성자가 아닌 경우에만 알림 발송
Long boardOwnerId = board.getUser().getIdx();
if (!boardOwnerId.equals(user.getIdx())) {
    notificationService.createNotification(
        boardOwnerId,
        NotificationType.BOARD_COMMENT,
        "내 게시글에 새로운 댓글이 달렸습니다",
        String.format("%s님이 댓글을 남겼습니다: %s", user.getUsername(),
            dto.getContent().length() > 50 ? dto.getContent().substring(0, 50) + "..." : dto.getContent()),
        board.getIdx(),
        "BOARD");
}
```

---

### 3. 실종 제보 게시글 댓글 알림 (`MISSING_PET_COMMENT`)

**발송 조건**:
- 누구나 실종 제보 게시글에 댓글 작성 시
- 댓글 작성자 ≠ 게시글 작성자인 경우에만 발송 (자기 자신에게 알림 방지)

**발송 위치**: `MissingPetBoardService.addComment()`

**알림 내용**:
- 제목: "실종 제보 게시글에 새로운 댓글이 달렸습니다"
- 내용: "{작성자명}님이 댓글을 남겼습니다: {댓글 내용 (50자 제한)}"
- 관련 ID: MissingPetBoard ID
- 관련 타입: "MISSING_PET"

**코드 예시**:
```java
// 알림 발송: 댓글 작성자가 게시글 작성자가 아닌 경우에만 알림 발송
Long boardOwnerId = board.getUser().getIdx();
if (!boardOwnerId.equals(user.getIdx())) {
    notificationService.createNotification(
        boardOwnerId,
        NotificationType.MISSING_PET_COMMENT,
        "실종 제보 게시글에 새로운 댓글이 달렸습니다",
        String.format("%s님이 댓글을 남겼습니다: %s", user.getUsername(),
            dto.getContent() != null && dto.getContent().length() > 50
                ? dto.getContent().substring(0, 50) + "..." : dto.getContent()),
        board.getIdx(),
        "MISSING_PET");
}
```

---

## 🏗️ 시스템 아키텍처

### 알림 도메인 구조

```
notification/
├── entity/
│   ├── Notification.java          # 알림 엔티티
│   └── NotificationType.java      # 알림 타입 Enum
├── dto/
│   └── NotificationDTO.java       # 알림 DTO
├── converter/
│   └── NotificationConverter.java # 엔티티-DTO 변환기
├── repository/
│   └── NotificationRepository.java # 알림 리포지토리
├── service/
│   └── NotificationService.java   # 알림 서비스
└── controller/
    └── NotificationController.java # 알림 컨트롤러
```

### 알림 엔티티 구조

```java
@Entity
public class Notification {
    private Long idx;                    // 알림 ID
    private Users user;                  // 알림을 받을 사용자
    private NotificationType type;        // 알림 타입
    private String title;                // 알림 제목
    private String content;              // 알림 내용
    private Long relatedId;              // 관련 게시글/댓글 ID
    private String relatedType;          // 관련 타입
    private Boolean isRead;              // 읽음 여부
    private LocalDateTime createdAt;    // 생성 시간
}
```

---

## 🔄 알림 발송 흐름도

```
댓글 작성
    ↓
댓글 저장 (DB)
    ↓
게시글 작성자 확인
    ↓
댓글 작성자 ≠ 게시글 작성자?
    ├─ Yes → 알림 발송
    │   ↓
    │   NotificationService.createNotification()
    │   ↓
    │   DB에 알림 저장
    │   ↓
    │   Redis에 실시간 알림 저장
    │   ↓
    │   완료
    └─ No → 알림 발송 안 함
```

---

## 💾 데이터 저장 전략

### 1. DB 저장 (영구 저장)
- 모든 알림을 `notifications` 테이블에 저장
- 읽음 여부, 생성 시간 등 영구 보관
- 사용자별 알림 조회 시 DB에서 조회

### 2. Redis 저장 (실시간 캐싱)
- 최신 알림 50개를 Redis에 저장
- Key: `notification:{userId}`
- TTL: 24시간
- 실시간 알림 조회 성능 향상

### 3. 병합 전략
- Redis와 DB 데이터를 병합하여 반환
- 중복 제거 후 최신순 정렬
- Redis에 없으면 DB에서만 조회

---

## 📡 API 엔드포인트

### 알림 목록 조회
```
GET /api/notifications?userId={userId}
```
- 현재 사용자의 모든 알림 조회
- 최신순 정렬

### 읽지 않은 알림 조회
```
GET /api/notifications/unread?userId={userId}
```
- 읽지 않은 알림만 조회

### 읽지 않은 알림 개수 조회
```
GET /api/notifications/unread/count?userId={userId}
```
- 읽지 않은 알림 개수만 반환

### 알림 읽음 처리
```
PUT /api/notifications/{notificationId}/read?userId={userId}
```
- 특정 알림을 읽음 처리

### 모든 알림 읽음 처리
```
PUT /api/notifications/read-all?userId={userId}
```
- 사용자의 모든 알림을 읽음 처리

---

## ⚙️ Redis 설정

### RedisTemplate 사용
- `notificationRedisTemplate`: 알림 리스트 저장용
- Key: `notification:{userId}`
- Value: `List<NotificationDTO>`
- TTL: 24시간

### RedisConfig에서 설정
```java
@Bean
public RedisTemplate<String, Object> notificationRedisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(createJsonRedisSerializer()); // Java 8 날짜/시간 지원
    // ...
}
```

---

## 🎯 주요 기능

### 1. 자동 알림 발송
- 댓글 작성 시 자동으로 알림 생성
- 게시글 작성자에게만 발송
- 자기 자신에게 알림 방지

### 2. 실시간 알림 관리
- Redis를 활용한 빠른 알림 조회
- 최신 알림 50개 캐싱
- DB와 Redis 병합으로 완전한 데이터 제공

### 3. 읽음 상태 관리
- 개별 알림 읽음 처리
- 전체 알림 읽음 처리
- 읽지 않은 알림 개수 조회

### 4. 성능 최적화
- Redis 캐싱으로 조회 성능 향상
- 최신 알림만 Redis에 저장 (최대 50개)
- DB와 Redis 병합으로 데이터 일관성 유지

---

## 📝 적용된 파일 목록

### 알림 도메인
- ✅ `Notification.java` - 알림 엔티티
- ✅ `NotificationType.java` - 알림 타입 Enum
- ✅ `NotificationDTO.java` - 알림 DTO
- ✅ `NotificationConverter.java` - 컨버터
- ✅ `NotificationRepository.java` - 리포지토리
- ✅ `NotificationService.java` - 알림 서비스
- ✅ `NotificationController.java` - 알림 컨트롤러

### 댓글 서비스 (알림 발송 로직 추가)
- ✅ `CareRequestCommentService.addComment()` - 펫케어 요청글 댓글 알림
- ✅ `CommentService.addComment()` - 커뮤니티 게시글 댓글 알림
- ✅ `MissingPetBoardService.addComment()` - 실종 제보 게시글 댓글 알림

---

## 🚀 사용 예시

### 프론트엔드에서 알림 조회
```javascript
// 읽지 않은 알림 개수 조회
const response = await axios.get(`/api/notifications/unread/count?userId=${userId}`);
const unreadCount = response.data;

// 알림 목록 조회
const notifications = await axios.get(`/api/notifications?userId=${userId}`);

// 알림 읽음 처리
await axios.put(`/api/notifications/${notificationId}/read?userId=${userId}`);
```

---

## ⚠️ 주의사항

1. **자기 자신에게 알림 방지**: 댓글 작성자와 게시글 작성자가 같으면 알림 발송하지 않음

2. **댓글 내용 길이 제한**: 알림 내용에 댓글 내용이 50자로 제한됨 (너무 긴 내용 방지)

3. **Redis TTL**: Redis에 저장된 알림은 24시간 후 자동 만료 (최신 알림만 유지)

4. **최대 알림 수**: Redis에는 최신 알림 50개만 저장 (메모리 효율성)

5. **데이터 일관성**: Redis와 DB 데이터를 병합하여 항상 완전한 데이터 제공

---

## 🔮 향후 확장 가능성

### 추가 가능한 알림 타입
- `CARE_APPLICATION_STATUS`: 펫케어 신청 상태 변경 알림
- `BOARD_LIKE`: 게시글 좋아요 알림 (선택적)
- `COMMENT_REPLY`: 대댓글 알림 (대댓글 기능 추가 시)

### 실시간 알림 푸시
- WebSocket을 활용한 실시간 알림 푸시
- 브라우저 알림 API 연동
- 모바일 푸시 알림 연동

### 알림 설정
- 사용자별 알림 수신 설정
- 알림 타입별 수신 여부 설정
- 알림 수신 시간대 설정

---

## 📚 참고 자료

- Redis Cache Strategy: `REDIS_CACHE_STRATEGY.md`
- Notification Service: `backend/main/java/com/linkup/Petory/domain/notification/service/NotificationService.java`
- Redis Config: `backend/main/java/com/linkup/Petory/global/security/RedisConfig.java`

