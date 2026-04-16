# Step 4 — Medium: 전역 API 응답 포맷 정리 (error/message 중복 제거)

## 목표
`GlobalExceptionHandler#handleApiException`에서 `error`와 `message` 키에 동일한 값이 중복 저장되는 문제를 제거하고, 응답을 `message` + `errorCode` + `status` 3-key 구조로 통일한다.

---

## 배경 · 위험

| 이슈 | 위치 | 리스크 |
|---|---|---|
| 3-7 | `GlobalExceptionHandler:109-110` | `error`와 `message`에 동일한 `e.getMessage()` 저장 → 불필요한 payload 중복 |

**이 변경은 전역(모든 도메인)에 영향**한다. 프론트엔드가 `error` 키를 사용하고 있다면 `message`로 전환 필요. Meetup 도메인 프론트 코드 및 다른 도메인 프론트 코드의 에러 핸들링 확인 후 진행.

---

## 현재 응답 구조 vs 변경 후

현재:
```json
{
  "error": "이미 참가한 모임입니다.",
  "message": "이미 참가한 모임입니다.",
  "status": 409,
  "errorCode": "MEETUP_ALREADY_JOINED"
}
```

변경 후:
```json
{
  "message": "이미 참가한 모임입니다.",
  "status": 409,
  "errorCode": "MEETUP_ALREADY_JOINED"
}
```

---

## 변경 상세

### `GlobalExceptionHandler#handleApiException`

**파일**: `backend/main/java/com/linkup/Petory/global/exception/GlobalExceptionHandler.java` (`:104-117`)

현재:
```java
@ExceptionHandler(ApiException.class)
public ResponseEntity<Map<String, Object>> handleApiException(ApiException e) {
    HttpStatus status = e.getHttpStatus();
    log.warn("API 예외: {} [{}] - {}", e.getErrorCode(), status, e.getMessage());

    Map<String, Object> response = new HashMap<>();
    response.put("error", e.getMessage());       // ← 중복
    response.put("message", e.getMessage());     // ← 중복
    response.put("status", status.value());
    if (e.getErrorCode() != null) {
        response.put("errorCode", e.getErrorCode());
    }

    return ResponseEntity.status(status).body(response);
}
```

변경 후:
```java
@ExceptionHandler(ApiException.class)
public ResponseEntity<Map<String, Object>> handleApiException(ApiException e) {
    HttpStatus status = e.getHttpStatus();
    log.warn("API 예외: {} [{}] - {}", e.getErrorCode(), status, e.getMessage());

    Map<String, Object> response = new HashMap<>();
    response.put("message", e.getMessage());
    response.put("status", status.value());
    if (e.getErrorCode() != null) {
        response.put("errorCode", e.getErrorCode());
    }

    return ResponseEntity.status(status).body(response);
}
```

**변경 요점**: `response.put("error", e.getMessage())` 한 줄 제거.

---

## 프론트엔드 영향 확인

변경 전에 프론트엔드 코드에서 `error` 키를 읽는 곳이 있는지 확인한다.

```bash
# 프론트엔드 에러 키 참조 검색
grep -r '"error"' frontend/src/
grep -r "\.error" frontend/src/api/
grep -r "response\.error\|data\.error\|res\.error" frontend/src/
```

`error` 키를 직접 참조하는 코드가 있으면 `message`로 변경하거나 두 키를 모두 읽도록 수정.

---

## Acceptance Criteria

```bash
# 컴파일 통과
./gradlew compileJava

# 수동 시나리오 (서버 기동 후)
# a. 존재하지 않는 모임 조회 → 응답 JSON에 "error" 키 없고 "message" 키만 있는지 확인
# b. 다른 도메인(user, board 등) 오류 응답도 동일 구조인지 확인
```

---

## 주의 사항

- Step 1, 2, 3이 완료된 상태에서 진행.
- **전역 변경**이므로 프론트엔드 영향 확인을 필수 선행.
- `GlobalExceptionHandler`의 다른 핸들러(`handleIllegalArgumentException` 등)는 이미 `error`와 `message`를 **다른 값**으로 쓰고 있으므로 이번 Step에서 건드리지 않음. 통일은 후속 PR.
