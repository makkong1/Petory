# Mobile FCM — 보안 이슈 & 버그 수정

Capacitor + FCM 푸시 알림 구현(2026-05-08) 이후 코드 검토에서 발견된 이슈들을 정리한 문서.

| 구분 | 의미 |
|------|------|
| **버그** | 잘못된 동작 — 기능 자체가 깨져 있음 |
| **보안** | 동작은 하지만 인증·인가 로직에 허점이 있음 |
| **품질** | 동작하지만 에러 처리·검증이 부실해 운영 상 문제로 이어질 수 있음 |

---

## 1. 버그 (기능 깨짐)

### 1.1 `removePushToken()` — 잘못된 API 사용으로 로그아웃 시 토큰 미삭제

| 항목 | 내용 |
|------|------|
| **파일** | `frontend/src/api/pushNotifications.js:46-55` |
| **심각도** | 🔴 CRITICAL |
| **상태** | ✅ 수정 완료 (2026-05-09) |

**증상**: 로그아웃 후에도 이전 사용자에게 푸시 알림이 계속 전달된다.

**원인**: `removePushToken()` 내부에서 FCM 등록 토큰을 가져오는 API 대신 수신된 알림 목록을 반환하는 API를 호출하고 있다.

```javascript
// Before (버그)
const token = await PushNotifications.getDeliveredNotifications();
// → 반환값: { notifications: [...] } 배열 객체. 토큰 문자열이 아님
// → if (token) 조건은 항상 truthy (빈 배열도 객체)
// → api.delete('/fcm/token', { data: { token } }) 에 잘못된 값 전달 → 서버 토큰 삭제 안 됨
```

```javascript
// After (수정안)
const { value: token } = await PushNotifications.getToken();
// → 반환값: { value: "fRhV9xxxxxxxx..." } — 실제 FCM 등록 토큰
if (token) {
    await api.delete('/fcm/token', { data: { token } });
}
```

---

## 2. 보안 이슈

### 2.1 FCM 토큰 삭제 시 소유자 검증 없음

| 항목 | 내용 |
|------|------|
| **파일** | `FcmTokenController.java:31-35`, `FcmService.java:47-49` |
| **심각도** | 🔴 CRITICAL |
| **상태** | ✅ 수정 완료 (2026-05-09) |

**증상**: 인증된 사용자가 타인의 FCM 토큰 문자열을 알고 있으면 그 토큰을 서버에서 삭제할 수 있다. 결과적으로 특정 사용자의 푸시 알림을 비활성화하는 것이 가능하다.

**원인**: `DELETE /api/fcm/token` 핸들러가 요청자 `userId`를 사용하지 않고 토큰 문자열만으로 즉시 삭제한다.

```java
// Before (보안 이슈)
@DeleteMapping("/token")
public ResponseEntity<Void> removeToken(@RequestBody FcmTokenRequestDTO dto) {
    fcmService.removeToken(dto.getToken());  // userId 미사용
    return ResponseEntity.ok().build();
}

// FcmService
public void removeToken(String token) {
    fcmTokenRepository.deleteByToken(token);  // 소유자 검증 없음
}
```

```java
// After (수정안)
@DeleteMapping("/token")
public ResponseEntity<Void> removeToken(@RequestBody FcmTokenRequestDTO dto) {
    Long userId = authenticatedUserIdResolver.requireCurrentUserIdx();
    fcmService.removeToken(userId, dto.getToken());
    return ResponseEntity.ok().build();
}

// FcmService
public void removeToken(Long userId, String token) {
    FcmToken fcmToken = fcmTokenRepository.findByToken(token).orElse(null);
    if (fcmToken == null) return;  // 없으면 no-op
    if (!fcmToken.getUser().getIdx().equals(userId)) return;  // 본인 소유 아니면 무시
    fcmTokenRepository.delete(fcmToken);
}
```

---

### 2.2 토큰 재등록 시 소유자 갱신 안 됨

| 항목 | 내용 |
|------|------|
| **파일** | `FcmService.java:36-43` |
| **심각도** | 🟡 HIGH |
| **상태** | ✅ 수정 완료 (2026-05-09) |

**증상**: 기기를 재사용하거나 앱 재설치 후 Firebase가 같은 토큰을 재발급할 때, 새 사용자가 등록 시도해도 DB에는 이전 사용자 귀속 상태 그대로 유지된다. 이전 사용자에게 알림이 계속 간다.

**원인**: `saveToken()` 코드가 토큰이 이미 DB에 존재하면 소유자 갱신 없이 그냥 skip 한다. 주석에는 "사용자 정보만 갱신"이라고 쓰여 있지만 실제로는 아무것도 하지 않아 주석이 틀렸다.

```java
// Before (버그 — 주석과 구현 불일치)
fcmTokenRepository.findByToken(token).ifPresentOrElse(
    existing -> log.debug("FCM 토큰 이미 등록됨: userId={}", userId),  // 갱신 없음
    () -> fcmTokenRepository.save(...)
);
```

```java
// After (수정안)
fcmTokenRepository.findByToken(token).ifPresentOrElse(
    existing -> {
        // 같은 사용자면 no-op, 다른 사용자면 소유권 이전
        if (!existing.getUser().getIdx().equals(userId)) {
            fcmTokenRepository.delete(existing);
            fcmTokenRepository.save(FcmToken.builder()
                    .user(user).token(token).deviceType(deviceType).build());
        }
    },
    () -> fcmTokenRepository.save(FcmToken.builder()
            .user(user).token(token).deviceType(deviceType).build())
);
```

---

## 3. 코드 품질

### 3.1 `FcmTokenRequestDTO` 입력 검증 없음

| 항목 | 내용 |
|------|------|
| **파일** | `FcmTokenRequestDTO.java` |
| **심각도** | 🟡 HIGH |
| **상태** | ✅ 수정 완료 (2026-05-09) — `build.gradle`에 `spring-boot-starter-validation` 추가 포함 |

**증상**: 빈 문자열 token이나 null deviceType이 DB에 그대로 저장되어 `sendToUser()` 루프에서 FCM 발송 실패가 반복 발생한다.

```java
// Before
public class FcmTokenRequestDTO {
    private String token;
    private FcmToken.DeviceType deviceType;
}

// After
public class FcmTokenRequestDTO {
    @NotBlank(message = "FCM token is required")
    private String token;

    @NotNull(message = "Device type is required")
    private FcmToken.DeviceType deviceType;
}
```

컨트롤러에 `@Valid` 추가도 필요:
```java
public ResponseEntity<Void> registerToken(@Valid @RequestBody FcmTokenRequestDTO dto)
```

---

### 3.2 FCM 에러코드 분기 불완전

| 항목 | 내용 |
|------|------|
| **파일** | `FcmService.java:85-91` |
| **심각도** | 🟠 MEDIUM |
| **상태** | ✅ 수정 완료 (2026-05-09) |

**증상**: `INVALID_ARGUMENT`, `SENDER_ID_MISMATCH` 등 다른 유형의 무효 토큰 에러는 처리되지 않아 DB에 불량 토큰이 잔존하고 매 알림마다 실패를 반복한다.

```java
// Before
if ("UNREGISTERED".equals(e.getMessagingErrorCode().name())) {
    fcmTokenRepository.deleteByToken(fcmToken.getToken());
}

// After
MessagingErrorCode errorCode = e.getMessagingErrorCode();
if (errorCode == MessagingErrorCode.UNREGISTERED
        || errorCode == MessagingErrorCode.INVALID_ARGUMENT
        || errorCode == MessagingErrorCode.SENDER_ID_MISMATCH) {
    log.info("FCM 토큰 무효로 삭제: token={}, reason={}", fcmToken.getToken(), errorCode);
    fcmTokenRepository.deleteByToken(fcmToken.getToken());
}
```

---

### 3.3 포그라운드 알림 in-app 처리 없음

| 항목 | 내용 |
|------|------|
| **파일** | `frontend/src/api/pushNotifications.js:33-35` |
| **심각도** | 🟠 MEDIUM |
| **상태** | ⬜ 미적용 — in-app 알림 UI 설계 별도 필요 (SSE 연동 or 토스트 컴포넌트) |

**증상**: 앱이 포그라운드 상태일 때 FCM이 수신되면 `console.log`만 출력되고 사용자에게 아무것도 표시되지 않는다. 일부 Android 기기는 포그라운드에서 OS 알림 배너를 자동으로 띄우지 않기 때문에 in-app 알림(토스트, 뱃지 등)이 별도 필요하다.

```javascript
// Before
PushNotifications.addListener('pushNotificationReceived', (notification) => {
    console.log('FCM 포그라운드 알림:', notification.title);
});

// After (SSE와 연동하거나 in-app 토스트 표시)
PushNotifications.addListener('pushNotificationReceived', (notification) => {
    // SSE 이미 연결되어 있으면 알림 UI는 SSE 흐름에서 처리됨
    // SSE 미연결 상황 대비 in-app 토스트 또는 뱃지 업데이트 추가 필요
    console.warn('FCM 포그라운드 수신 — in-app 처리 미구현:', notification.title);
});
```

---

### 3.4 `saveToPreferences` 에러 무음 처리

| 항목 | 내용 |
|------|------|
| **파일** | `frontend/src/api/tokenStorage.js:15` |
| **심각도** | 🔵 LOW |
| **상태** | ✅ 수정 완료 (2026-05-09) |

**증상**: Capacitor Preferences 저장 실패 시 원인을 알 수 없다. 일부 WebView 환경에서 localStorage가 초기화되면 앱 재시작 후 로그인 상태가 유실된다.

```javascript
// Before
} catch (_) {}

// After
} catch (error) {
    console.warn('Capacitor Preferences 저장 실패:', error);
}
```

---

## 4. 수정 결과 요약

| 순서 | 이슈 | 파일 | 구분 | 상태 |
|------|------|------|------|------|
| 1 | `removePushToken()` 잘못된 API | `pushNotifications.js:50` | 버그 | ✅ 완료 |
| 2 | 토큰 삭제 소유자 검증 없음 | `FcmTokenController.java:31`, `FcmService.java:47` | 보안 | ✅ 완료 |
| 3 | 토큰 재등록 소유자 갱신 안 됨 | `FcmService.java:36` | 보안 | ✅ 완료 |
| 4 | DTO 입력 검증 추가 | `FcmTokenRequestDTO.java` | 품질 | ✅ 완료 |
| 5 | FCM 에러코드 분기 확대 | `FcmService.java:88` | 품질 | ✅ 완료 |
| 6 | 포그라운드 알림 in-app 처리 | `pushNotifications.js:33` | 품질 | ⬜ 미적용 |
| 7 | `saveToPreferences` 에러 로깅 | `tokenStorage.js:15` | 품질 | ✅ 완료 |
