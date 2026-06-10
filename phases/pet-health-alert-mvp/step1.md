# Step 1 — NotificationType.PET_HEALTH_ALERT 추가

## 목적
`PET_HEALTH_ALERT` enum 값이 없으면 Step 3의 `createNotification()` 호출 시 컴파일 에러가 난다.
다른 모든 step의 전제조건이므로 가장 먼저 실행한다.

## 수정 대상
`backend/main/java/com/linkup/Petory/domain/notification/entity/NotificationType.java`

## 현재 상태
```java
public enum NotificationType {
    CARE_REQUEST_COMMENT,
    BOARD_COMMENT,
    MISSING_PET_COMMENT
}
```

## 변경 후
```java
public enum NotificationType {
    CARE_REQUEST_COMMENT,
    BOARD_COMMENT,
    MISSING_PET_COMMENT,
    PET_HEALTH_ALERT      // MEDICAL+HIGH urgency signal 저장 시 발송
}
```

## 변경 범위
- 1개 파일, 1줄 추가
- 기존 enum 값에 영향 없음

## AC (Acceptance Criteria)
```bash
./gradlew compileJava
```
컴파일 오류 없이 통과해야 한다.
