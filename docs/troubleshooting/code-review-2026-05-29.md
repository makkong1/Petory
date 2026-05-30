# 코드 리뷰 결과 — 2026-05-29

> **범위**: `dev` 브랜치 → `main` 브랜치 diff (커밋 약 20개)  
> **리뷰 방법**: 7-앵글 멀티 에이전트 → 사용자 코드 검증 2-pass  
> **기준 날짜**: 2026-05-29  

---

## 우선순위 요약

| 우선순위 | 항목 | 파일 | 상태 |
|---------|------|------|------|
| P1 | parseCoord NFE → 추천 지도 좌표 전부 null | `PetDataApiClient.java:421` | ✅ CONFIRMED |
| P1 | sendEvents no-op → 추천 이벤트 전부 소실 | `PetDataApiClient.java:160` | ✅ CONFIRMED |
| P1 | csvFilePath 경로 순회 미검증 | `AdminLocationController.java:175` | ✅ CONFIRMED |
| P1 | jsonPreview Files.readString 크기 제한 없음 | `LocationServiceAdminController.java:112` | ✅ CONFIRMED |
| P3 | findByRadius 반경 내 전체 로드 후 Java limit (SQL LIMIT 없음) | `LocationServiceService.java:199` | ✅ CONFIRMED |
| P2 | @Transactional 없는 update+batch → 부분 커밋 | `LocationImportService.java:77` | ⚠️ PLAUSIBLE |
| P2 | 루프 내 단건 SELECT (N+1) | `LocationImportService.java:61` | ✅ CONFIRMED |
| P2 | 단건 toDTO boardFilePath 항상 null (잠복) | `BoardPopularitySnapshotConverter.java:41` | ✅ CONFIRMED |
| P2 | validateSyncPath dead code → 오해 유발 에러 | `LocationServiceAdminController.java:227` | ✅ CONFIRMED |
| P3 | popularLocationServices @CacheEvict 없음 | `LocationServiceService.java:105` | ⚠️ PARTIAL |
| P3 | boardDetail @CacheEvict no-op | `BoardService.java:277` | ✅ CONFIRMED |
| P3 | 프로그래밍 에러를 HTTP 400으로 처리 | `GlobalExceptionHandler.java:108` | ⚠️ PLAUSIBLE |
| P3 | CANCELLED 이중 환불 — 비관적 락으로 실질 방어 | `CareRequestService.java:350` | ⚠️ PLAUSIBLE |

---

## P1 — 즉시 수정 권장

### 1. parseCoord: Long.parseLong → 소수 좌표 시 NFE → null 반환

**파일**: `domain/recommendation/client/PetDataApiClient.java:417–425`

```java
private static Double parseCoord(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
        return Long.parseLong(raw.trim()) / 10_000_000.0; // ← 소수 오면 NFE → null
    } catch (NumberFormatException e) {
        return null; // 조용히 null 반환
    }
}
```

**원인**: Naver Map API의 정수×10^7 인코딩 방식을 가정. pet-data-api가 `"127.0276368"` 같은 소수 형식으로 응답하면 `Long.parseLong` 실패 → 모든 FacilityItem의 `lat`, `lng` = null.

**영향**: 인기 시설 추천 카드의 지도 핀이 전부 사라짐. 거리 기반 랭킹도 동작 안 함.

**수정 방향**:
```java
return Double.parseDouble(raw.trim()); // 소수·정수 모두 처리
// 정수×10^7 형식이라면: val > 1000 ? val / 10_000_000.0 : val 분기
```

---

### 2. sendEvents: HTTP 없는 no-op — 추천 이벤트 전부 소실

**파일**: `domain/recommendation/client/PetDataApiClient.java:159–162`

```java
@SuppressWarnings("UseSpecificCatch")
public void sendEvents(RecommendEventRequest request) {
    log.debug("[PetDataApiClient/events] skipped — popularity API 에 이벤트 수집 경로 없음");
    // HTTP 전송 없음
}
```

**원인**: pet-data-api 이벤트 수집 엔드포인트가 없어서 no-op으로 처리. 하지만 `RecommendService.recordEvents()`는 이 메서드가 실제로 전송한다고 가정하고 호출.

**영향**: 사용자의 클릭/노출 이벤트가 모두 소실 → 추천 모델 피드백 루프 단절 → 인기 점수 갱신 없음.

**수정 방향**: pet-data-api에 `/events` 엔드포인트 생성하거나, 이벤트를 로컬 DB/큐에 저장하는 대체 경로 구현. 현재 상태라면 최소한 `log.warn`으로 격상해 운영 중 감지 가능하도록.

---

### 3. csvFilePath 경로 순회 미검증

**파일**: `domain/admin/controller/AdminLocationController.java:172–189`

```java
@PostMapping("/import-public-data-path")
@PreAuthorize("hasRole('MASTER')")
public ResponseEntity<BatchImportResult> importPublicDataByPath(
        @RequestParam("csvFilePath") String csvFilePath) {

    log.info("공공데이터 CSV 경로 임포트 요청: {}", csvFilePath);

    try {
        BatchImportResult result = publicDataLocationService.importFromCsv(csvFilePath);
        // ↑ csvFilePath를 Path.of()로 그대로 전달 — traversal 체크 없음
```

**대조**: 같은 패키지의 JSON import(`LocationServiceAdminController`)는 `resolveAndValidatePath()`로 `normalize().toAbsolutePath()` 후 `../` 포함 여부 검증.

**원인**: CSV 경로 엔드포인트에만 동일한 검증이 빠져 있음.

**영향**: MASTER 권한 계정이 `csvFilePath=../../etc/passwd` 전달 시 서버 내부 파일 파싱 시도. 응답에 파일 내용이 직접 노출되진 않지만, 파싱 예외 메시지에 내부 경로 정보가 포함될 수 있음.

**수정 방향**: JSON import와 동일하게 `resolveAndValidatePath()` 적용, 또는 허용 기본 디렉토리 prefix 검증 추가.

---

### 4. jsonPreview: Files.readString — 파일 크기 제한 없음

**파일**: `domain/location/controller/LocationServiceAdminController.java:107–127`

```java
if (!Files.exists(path)) {  // ← resolveAndValidatePath가 먼저 throw하므로 dead code
    return ResponseEntity.ok(Map.of("exists", false, "reason", "파일 없음"));
}

try {
    String content = Files.readString(path, StandardCharsets.UTF_8); // ← 크기 제한 없음
    List<LocationImportDto> records = objectMapper.readValue(content, new TypeReference<>() {});
```

**원인 1 (OOM)**: HTTP 업로드 엔드포인트는 200MB 제한이 있지만, 서버 경로(`importFilePath`) 기반 preview에는 크기 체크가 없음. `importFilePath`가 수 GB 파일을 가리키면 힙 고갈.

**원인 2 (dead code)**: `resolveAndValidatePath()`(line 255)가 `Files.isRegularFile` false일 때 `IllegalArgumentException("경로가 파일이 아닙니다.")`을 먼저 throw하므로, 그 아래의 `Files.exists` 체크는 실행되지 않음. 파일 미존재 시 "파일 없음" 대신 "경로가 파일이 아닙니다." 에러가 반환되어 운영자를 혼란스럽게 만듦.

**수정 방향**:
```java
// 크기 제한 추가
long size = Files.size(path);
if (size > 50 * 1024 * 1024L) {
    return ResponseEntity.badRequest().body(Map.of("error", "미리보기 크기 초과 (50MB 이하만 가능)"));
}

// resolveAndValidatePath에서 isRegularFile 체크를 exists 체크와 분리
```

---

### 5. findByRadius: 줌 레벨 size가 SQL LIMIT으로 내려가지 않음 (미완성)

**파일**: `domain/location/service/LocationServiceService.java:199–209`

> 버그가 아니라 **미완성 구현**. 프론트엔드에서 줌 레벨 기반 제한은 이미 구현되어 있음.

**현재 흐름:**
```
프론트(unifiedMapApi.js:91): size = getLimitForLevel('location', mapLevel)
  → zoom 7 → size=150 → API 전송

Controller: effectiveSize = 150
Service: maxResults = 150

findByRadius SQL: ST_Within + ST_Distance_Sphere (LIMIT 없음) → 반경 내 전체 로드
Java: stream().limit(150) → 150개로 자름  ← 여기서 비로소 적용
```

**프론트 zoom 테이블** (`unifiedMapApi.js:65`):
```js
const ZOOM_LIMIT_TABLE = {
  location: { 4: 30, 5: 50, 6: 100, 7: 150, 8: 250, 9: 400, default: 500 },
};
```

**문제**: `size=150`이 컨트롤러까지 도달하지만 SQL에서 `LIMIT 150`으로 쓰이지 않음. 반경 내 3,000개가 있으면 DB가 3,000건 로드 후 Java가 150개로 자름.

**수정 방향**: `findByRadius`에 `LIMIT :limit` 파라미터 추가.
```java
// Repository 메서드에 limit 파라미터 추가
List<LocationService> findByRadius(..., @Param("limit") int limit);

// 서비스에서 maxResults 전달
int limit = (maxResults != null && maxResults > 0) ? maxResults : 500;
locationServiceRepository.findByRadius(..., limit);
```

---

## P2 — 데이터 정합성 리스크

### 6. processEntries: @Transactional 없음 → update·batch INSERT 분리 커밋

**파일**: `domain/location/service/LocationImportService.java:53–96`

```java
private SyncResult processEntries(List<LocationImportDto> dtos) { // @Transactional 없음
    // ...
    if (existing.isPresent()) {
        // ...
        locationServiceRepository.save(entity); // ← auto-commit 트랜잭션
        updated++;
    } else {
        batch.add(locationImportConverter.toEntity(dto));
        if (batch.size() >= batchSize) {
            saved += batchWriter.saveBatch(batch); // ← REQUIRES_NEW 별도 트랜잭션
        }
    }
    if (!batch.isEmpty()) saved += batchWriter.saveBatch(batch); // ← 마지막 배치
}
```

**원인**: UPDATE는 메서드 호출마다 독립적으로 커밋되고, INSERT 배치는 `LocationServiceBatchWriter.saveBatch` (`@Transactional(REQUIRES_NEW)`)로 별도 트랜잭션. `processEntries` 자체에 트랜잭션이 없으므로 마지막 배치 실패 시 그 이전에 커밋된 UPDATE는 롤백 불가.

**영향**: 10,000건 import 도중 5,000번째 saveBatch 실패 → 이전 UPDATE는 이미 DB에 반영, 이후 INSERT는 미반영 → SyncResult 카운트 불일치 + 데이터 부분 반영 상태.

**수정 방향**: `processEntries`에 `@Transactional` 추가 (UPDATE/INSERT 전체를 하나의 경계로 묶음). 단, `batchWriter`의 `REQUIRES_NEW`와의 관계 재검토 필요.

---

### 7. 루프 내 단건 SELECT (N+1 패턴)

**파일**: `domain/location/service/LocationImportService.java:61–62`

```java
for (LocationImportDto dto : dtos) {
    Optional<LocationService> existing = locationServiceRepository
            .findByNameAndAddressAndDataSource(dto.getName(), dto.getAddress(), "BATCH_IMPORT");
    // ↑ 레코드 수만큼 SELECT 실행
```

**영향**: 5,000건 import → 최대 5,000번 SELECT 왕복. UPDATE 분기 레코드는 추가로 `save()` 1회 → N SELECT + M UPDATE.

**수정 방향**:
- `IN (name, address)` 조합으로 bulk 조회 후 Map 구성, 이후 루프에서 Map 참조
- 또는 DB 레벨 `INSERT ... ON DUPLICATE KEY UPDATE`

---

### 8. 단건 toDTO: boardFilePath 항상 null (잠복 버그)

**파일**: `domain/board/converter/BoardPopularitySnapshotConverter.java:26–43`

현재 주 API 경로(`BoardPopularityService.java:82`)는 `toDTOList()`를 사용하므로 즉시 노출되지는 않음. 그러나 단건 오버로드가 공개 메서드로 존재:

```java
public BoardPopularitySnapshotDTO toDTO(BoardPopularitySnapshot snapshot) {
    return toDTO(snapshot, null); // boardFilePath 항상 null — 첨부 파일 미조회
}

// toDTOList는 getAttachmentsBatch()로 이미지 URL 정상 조회
public List<BoardPopularitySnapshotDTO> toDTOList(List<BoardPopularitySnapshot> snapshots) {
    Map<Long, String> fileMap = attachmentFileService.getAttachmentsBatch(...); // ✅
    // ...
}
```

**수정 방향**: 단건 `toDTO(snapshot)`에서도 `attachmentFileService.getAttachments(boardId)`를 호출하도록 수정하거나, 단건 오버로드를 `@Deprecated` 처리하고 `toDTOList` 사용을 강제.

---

### 9. validateSyncPath dead code + 오해 유발 에러 메시지

**파일**: `domain/location/controller/LocationServiceAdminController.java:245–256`

```java
if (!Files.isRegularFile(normalized)) {
    throw new IllegalArgumentException("경로가 파일이 아닙니다."); // 파일 미존재 시도 여기서 throw
}
```

```java
// 호출부 (line 107, 233):
if (!Files.exists(path)) {  // ← Files.isRegularFile이 먼저 throw하므로 dead code
    return ResponseEntity.ok(Map.of("exists", false, "reason", "파일 없음"));
}
```

**영향**: 파일이 없을 때 "파일 없음"이 아닌 "경로가 파일이 아닙니다." 응답 → 운영자가 경로 설정 문제로 오인할 수 있음.

**수정 방향**: `resolveAndValidatePath`에서 `Files.exists` 체크를 `Files.isRegularFile`보다 먼저 실행:
```java
if (!Files.exists(normalized)) throw new IllegalArgumentException("파일 없음");
if (!Files.isRegularFile(normalized)) throw new IllegalArgumentException("경로가 파일이 아닙니다.");
```

---

## P3 — 캐시·에러 처리

### 10. popularLocationServices: @CacheEvict 없음 → 최대 30분 stale

**파일**: `domain/location/service/LocationServiceService.java:105`

```java
@Cacheable(value = "popularLocationServices", key = "#p0")
public List<LocationServiceDTO> getPopularLocationServices(String category) { ... }
```

**근거**: `RedisConfig.java:161`에 기본 TTL 30분 설정. `popularLocationServices`는 별도 TTL 미설정 → 기본값 적용. 대응하는 `@CacheEvict`가 코드베이스 어디에도 없음.

**영향**: `LocationImportService`로 신규 시설 200개 임포트 후 최대 30분간 이전 인기 시설 목록 노출.

**수정 방향**: `LocationImportService.importFromFile()` 완료 후 `@CacheEvict(value = "popularLocationServices", allEntries = true)` 추가.

---

### 11. boardDetail @CacheEvict no-op

**파일**: `domain/board/service/BoardService.java:239, 277`

```java
// getBoard에서 @Cacheable 제거됨 (주석으로 명시)
public BoardDTO getBoard(Long idx) { ... } // @Cacheable 없음

// 하지만 updateBoard, deleteBoard, restoreBoard 등에 여전히:
@CacheEvict(value = "boardDetail", key = "#idx")
public BoardDTO updateBoard(...) { ... }
```

**근거**: `RedisConfig.java:212`에 `boardDetail` 캐시가 1시간 TTL로 등록되어 있지만, `@Cacheable`이 없어 키가 생성되지 않음 → 모든 evict는 존재하지 않는 키에 대한 DEL.

**영향**: 현재는 기능 버그 없음 (evict가 no-op일 뿐). 그러나 미래에 `getBoard`에 `@Cacheable` 재추가 시, `key = "#p0"` vs `key = "#idx"` 불일치 등으로 stale cache 버그 발생 가능.

**수정 방향**: `@CacheEvict` 일괄 제거 또는 `getBoard`에 `@Cacheable` 복원 중 하나로 일관성 유지.

---

### 12. InvalidDataAccessApiUsageException → HTTP 400 처리

**파일**: `global/exception/GlobalExceptionHandler.java:108`

```java
@ExceptionHandler(InvalidDataAccessApiUsageException.class)
public ResponseEntity<Map<String, Object>> handleInvalidDataAccess(...) {
    // null PK 같은 JPA 프로그래밍 에러를 400 Bad Request로 반환
}
```

**원인**: `CareRequestController`의 `userId` null 전달 방어를 위해 추가된 핸들러. 하지만 `InvalidDataAccessApiUsageException`은 클라이언트 오류가 아닌 서버 프로그래밍 오류 (null ID를 JPA에 전달).

**영향**: 5xx 모니터링 알림이 발생하지 않음 → 프로그래밍 버그가 클라이언트 오류로 오인되어 프로덕션에서 조용히 묻힘. `log.warn`으로 남기더라도 5xx 기반 알림 시스템에서 감지 불가.

**수정 방향**: `userId` 전달 경로를 서비스 레이어에서 principal 기반으로 수정 (근본 원인 제거) → 핸들러 불필요해짐. 핸들러는 500으로 유지하거나 제거.

---

### 13. CANCELLED 이중 환불 — 비관적 락으로 실질 방어됨

**파일**: `domain/care/service/CareRequestService.java:350`

```java
if (newStatus == CareRequestStatus.CANCELLED) {
    // oldStatus != CANCELLED 체크 없음
    petCoinEscrowService.refundToRequester(escrow);
}
```

**방어 코드 확인** (`PetCoinEscrowService.java:137–156`):
```java
PetCoinEscrow lockedEscrow = escrowRepository.findByIdForUpdate(escrow.getIdx()); // 비관적 락
if (lockedEscrow.getStatus() != EscrowStatus.HOLD) {
    throw new IllegalStateException("HOLD 상태의 에스크로만 환불할 수 있습니다.");
}
// ... 환불 처리
escrow.setStatus(EscrowStatus.REFUNDED); // 완료 후 상태 변경
```

**현재 상태**: 비관적 락 + HOLD 체크로 실질적 이중 환불은 방지됨. 단, 도메인 서비스(`CareRequestService`) 레이어에서 상태 전환 멱등성을 에스크로 서비스에 의존하는 구조는 취약.

**개선 여지**: `updateStatus`에서 `oldStatus != CANCELLED` 선행 체크로 도메인 레이어 자체 방어 추가.

---

## 오탐 기록 (Not Confirmed)

면접 대비 — "왜 버그가 아닌가"도 설명할 수 있어야 함.

| 후보 | 오탐 이유 |
|------|----------|
| `CareRequestService:372` AND/OR 파싱 | `JpaCareRequestAdapter:82–89`에서 Spring Data 메서드명 대신 `findIdxByFulltextKeyword` (native query, `is_deleted=0` 포함)로 완전히 대체 |
| `CareRequestService:86` valueOf → 500 | `GlobalExceptionHandler:77`에 `@ExceptionHandler(IllegalArgumentException.class)` 명시 → 400 반환 |
| `CommentService:206` null content NPE | `CommentDTO:21`에 `@NotBlank content`, `BoardController:128`의 `@Valid`로 REST 경로 차단. 서비스 직접 호출 경로에서만 위험 |
| `FileUploadController:79` 인증 없음 | `SecurityConfig:69`에서 `GET /api/uploads/**` 명시적 `permitAll()` — 설계 의도 (공개 파일 접근 정책) |

---

## 면접 연결 포인트

| 발견 | CS 주제 |
|------|---------|
| findByRadius 공간 인덱스 필터 후 SQL LIMIT 없음 | **공간 인덱스(ST_Within), SQL LIMIT 위치, Java vs DB 레벨 필터링** |
| processEntries @Transactional 없음 | **트랜잭션 전파 (REQUIRED vs REQUIRES_NEW), 부분 커밋** |
| 루프 내 단건 SELECT | **N+1 문제, bulk query 최적화** |
| @Cacheable + @CacheEvict 불일치 | **캐시 일관성, TTL vs 명시적 eviction** |
| parseCoord silent null | **방어적 파싱, fail-fast vs silent failure** |
| CANCELLED 멱등성 | **멱등성(idempotency), 비관적 락의 용도** |
| csvFilePath traversal | **Path traversal 공격, 입력 검증 레이어** |
