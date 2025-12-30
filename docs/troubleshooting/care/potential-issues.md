# 펫케어 서비스 트러블슈팅 가이드

> **참고**: 이 문서는 `docs/domains/care.md`와 실제 백엔드 로직을 대조하여 작성되었습니다.

## 발견된 잠재적 문제점들

### 1. 권한 검증 부재 (심각) ⚠️

#### 1.1 CareRequest 수정/삭제 권한 검증 없음
**위치**: 
- `CareRequestService.updateCareRequest()` (라인 104-132)
- `CareRequestService.deleteCareRequest()` (라인 134-142)
- `CareRequestController.updateCareRequest()` (라인 43-46)
- `CareRequestController.deleteCareRequest()` (라인 48-53)

**현재 상태**:
- 컨트롤러에서 `userId`를 파라미터로 받지 않음
- 서비스 메서드에서 작성자 확인 로직이 없음
- **누구나 다른 사람의 케어 요청을 수정/삭제할 수 있는 보안 취약점 존재**

**실제 코드**:
```java
// CareRequestController.java
@PutMapping("/{id}")
public ResponseEntity<CareRequestDTO> updateCareRequest(@PathVariable Long id, @RequestBody CareRequestDTO dto) {
    return ResponseEntity.ok(careRequestService.updateCareRequest(id, dto));
}

// CareRequestService.java
@Transactional
public CareRequestDTO updateCareRequest(Long idx, CareRequestDTO dto) {
    CareRequest request = careRequestRepository.findById(idx)
            .orElseThrow(() -> new RuntimeException("CareRequest not found"));
    // 작성자 확인 없이 바로 수정 진행
    // ...
}
```

**영향**:
- 보안 취약점: 다른 사용자의 요청을 악의적으로 수정/삭제 가능
- 데이터 무결성 문제
- 사용자 신뢰도 하락

**해결 방안**:
```java
// 컨트롤러에서 인증된 사용자 ID 추출 (SecurityContext 사용)
@PutMapping("/{id}")
public ResponseEntity<CareRequestDTO> updateCareRequest(
        @PathVariable Long id, 
        @RequestBody CareRequestDTO dto,
        Authentication authentication) {
    Long currentUserId = Long.parseLong(authentication.getName());
    return ResponseEntity.ok(careRequestService.updateCareRequest(id, dto, currentUserId));
}

// 서비스에서 작성자 확인 추가
@Transactional
public CareRequestDTO updateCareRequest(Long idx, CareRequestDTO dto, Long currentUserId) {
    CareRequest request = careRequestRepository.findById(idx)
            .orElseThrow(() -> new RuntimeException("CareRequest not found"));
    
    // 작성자 확인 추가
    if (!request.getUser().getIdx().equals(currentUserId)) {
        throw new RuntimeException("본인의 케어 요청만 수정할 수 있습니다.");
    }
    
    // 기존 로직...
}
```

#### 1.2 CareRequest 상태 변경 권한 검증 없음
**위치**: 
- `CareRequestService.updateStatus()` (라인 154-163)
- `CareRequestController.updateStatus()` (라인 61-66)

**현재 상태**:
- 누구나 케어 요청의 상태를 변경할 수 있음
- 작성자와 승인된 케어 제공자만 상태를 변경할 수 있어야 함

**실제 코드**:
```java
@PatchMapping("/{id}/status")
public ResponseEntity<CareRequestDTO> updateStatus(@PathVariable Long id, @RequestParam String status) {
    return ResponseEntity.ok(careRequestService.updateStatus(id, status));
}

@Transactional
public CareRequestDTO updateStatus(Long idx, String status) {
    CareRequest request = careRequestRepository.findById(idx)
            .orElseThrow(() -> new RuntimeException("CareRequest not found"));
    // 권한 확인 없이 바로 상태 변경
    request.setStatus(CareRequestStatus.valueOf(status));
    // ...
}
```

**해결 방안**:
```java
@Transactional
public CareRequestDTO updateStatus(Long idx, String status, Long currentUserId) {
    CareRequest request = careRequestRepository.findByIdWithApplications(idx)
            .orElseThrow(() -> new RuntimeException("CareRequest not found"));
    
    // 작성자 또는 승인된 제공자만 상태 변경 가능
    boolean isRequester = request.getUser().getIdx().equals(currentUserId);
    boolean isAcceptedProvider = request.getApplications() != null && 
            request.getApplications().stream()
                    .anyMatch(app -> app.getStatus() == CareApplicationStatus.ACCEPTED 
                            && app.getProvider().getIdx().equals(currentUserId));
    
    if (!isRequester && !isAcceptedProvider) {
        throw new RuntimeException("작성자 또는 승인된 제공자만 상태를 변경할 수 있습니다.");
    }
    
    // 상태 전이 검증 추가 (아래 7번 참고)
    validateStatusTransition(request.getStatus(), CareRequestStatus.valueOf(status));
    
    request.setStatus(CareRequestStatus.valueOf(status));
    CareRequest updated = careRequestRepository.save(request);
    return careRequestConverter.toDTO(updated);
}
```

### 2. CareApplication 관리 방식 확인 ✅

**참고**: `care.md`에 따르면 `CareApplication`은 채팅 도메인(`ConversationService`)에서 관리됩니다.

**실제 구현 확인**:
- ✅ `ConversationService.confirmCareDeal()` (라인 546-652)에서 CareApplication 생성/승인 처리
- ✅ 양쪽 모두 거래 확정 시 자동으로 CareApplication 생성 및 ACCEPTED 상태로 설정
- ✅ 기존 CareApplication이 있으면 승인 상태로 변경

**결론**: 
- CareApplication 관리 로직은 **정상적으로 구현되어 있음**
- 채팅을 통한 거래 확정 방식으로 동작
- 별도의 CareApplicationService가 필요하지 않음

**단, 개선 가능한 점**:
- 동시에 여러 지원을 승인하는 경우를 방지하기 위한 추가 검증 필요 (아래 3번 참고)

### 3. 동시성 문제 (중간) ⚠️

#### 3.1 여러 지원 동시 승인 가능
**위치**: `ConversationService.confirmCareDeal()` (라인 614-631)

**현재 상태**:
- 양쪽 모두 거래 확정 시 기존 CareApplication을 찾아서 승인하거나 새로 생성
- 하지만 **다른 채팅방에서 동시에 거래 확정하면 여러 CareApplication이 ACCEPTED 상태가 될 수 있음**

**실제 코드**:
```java
// 기존 CareApplication 찾기
CareApplication existingApplication = careRequest.getApplications() != null
        ? careRequest.getApplications().stream()
                .filter(app -> app.getProvider().getIdx().equals(providerId))
                .findFirst()
                .orElse(null)
        : null;

if (existingApplication == null) {
    // 새로 생성
    CareApplication newApplication = CareApplication.builder()
            .status(CareApplicationStatus.ACCEPTED)
            .build();
    // ...
} else {
    // 기존 것 승인
    existingApplication.setStatus(CareApplicationStatus.ACCEPTED);
}
```

**문제점**:
- 다른 제공자와의 채팅방에서도 동시에 거래 확정하면 여러 ACCEPTED 지원이 생길 수 있음
- 한 요청에는 하나의 ACCEPTED 지원만 있어야 함

**해결 방안**:
```java
// 다른 ACCEPTED 지원이 있으면 REJECTED로 변경
if (existingApplication == null) {
    // 다른 ACCEPTED 지원 확인
    Optional<CareApplication> otherAccepted = careRequest.getApplications() != null
            ? careRequest.getApplications().stream()
                    .filter(app -> app.getStatus() == CareApplicationStatus.ACCEPTED)
                    .findFirst()
            : Optional.empty();
    
    if (otherAccepted.isPresent()) {
        otherAccepted.get().setStatus(CareApplicationStatus.REJECTED);
    }
    
    // 새로 생성
    CareApplication newApplication = CareApplication.builder()
            .status(CareApplicationStatus.ACCEPTED)
            .build();
    // ...
} else {
    // 기존 것 승인 전에 다른 ACCEPTED 지원 확인
    Optional<CareApplication> otherAccepted = careRequest.getApplications() != null
            ? careRequest.getApplications().stream()
                    .filter(app -> app.getStatus() == CareApplicationStatus.ACCEPTED
                            && !app.getIdx().equals(existingApplication.getIdx()))
                    .findFirst()
            : Optional.empty();
    
    if (otherAccepted.isPresent()) {
        otherAccepted.get().setStatus(CareApplicationStatus.REJECTED);
    }
    
    existingApplication.setStatus(CareApplicationStatus.ACCEPTED);
}
```

#### 3.2 동일 사용자 중복 지원 방지
**현재 상태**:
- `ConversationService.confirmCareDeal()`에서 기존 CareApplication을 찾아서 재사용
- 하지만 **직접 지원 API가 없어서 중복 지원 문제는 발생하지 않음** (채팅 기반이므로)

**결론**: 현재 구조에서는 문제 없음

### 4. 스케줄러 로직 문제 (중간) ⚠️

**위치**: `CareRequestScheduler.updateExpiredCareRequests()` (라인 34-60)

**현재 상태**:
- 날짜가 지난 `OPEN` 또는 `IN_PROGRESS` 상태의 요청을 모두 `COMPLETED`로 변경
- **`IN_PROGRESS` 상태는 실제로 진행 중일 수 있음** (예: 며칠간의 장기 케어)

**실제 코드**:
```java
@Scheduled(cron = "0 0 * * * ?")
@Transactional
public void updateExpiredCareRequests() {
    LocalDateTime now = LocalDateTime.now();
    
    // 날짜가 지났고, OPEN 또는 IN_PROGRESS 상태인 요청 조회
    List<CareRequest> expiredRequests = careRequestRepository
            .findByDateBeforeAndStatusIn(
                    now,
                    List.of(CareRequestStatus.OPEN, CareRequestStatus.IN_PROGRESS));
    
    // 모두 COMPLETED로 변경
    for (CareRequest request : expiredRequests) {
        request.setStatus(CareRequestStatus.COMPLETED);
    }
}
```

**문제점**:
- `date` 필드가 케어 시작일인지 종료일인지 명확하지 않음
- `IN_PROGRESS` 상태는 실제로 진행 중일 수 있음
- 자동으로 `COMPLETED`로 변경하면 사용자가 수동으로 완료 처리할 수 없음

**해결 방안**:
```java
@Scheduled(cron = "0 0 * * * ?")
@Transactional
public void updateExpiredCareRequests() {
    LocalDateTime now = LocalDateTime.now();
    
    // OPEN 상태만 자동으로 COMPLETED로 변경
    // IN_PROGRESS는 수동 완료만 허용
    List<CareRequest> expiredOpenRequests = careRequestRepository
            .findByDateBeforeAndStatusIn(now, List.of(CareRequestStatus.OPEN));
    
    for (CareRequest request : expiredOpenRequests) {
        request.setStatus(CareRequestStatus.COMPLETED);
        log.debug("만료된 요청 자동 완료: id={}, title={}, date={}", 
                request.getIdx(), request.getTitle(), request.getDate());
    }
    
    careRequestRepository.saveAll(expiredOpenRequests);
    
    // IN_PROGRESS 상태는 경고만 로깅 (수동 완료 필요)
    List<CareRequest> expiredInProgress = careRequestRepository
            .findByDateBeforeAndStatusIn(now, List.of(CareRequestStatus.IN_PROGRESS));
    
    if (!expiredInProgress.isEmpty()) {
        log.warn("날짜가 지났지만 진행 중인 요청이 있습니다. 수동 완료가 필요합니다. count={}", 
                expiredInProgress.size());
    }
}
```

**또는**:
- `startDate`와 `endDate`를 분리하여 관리
- `endDate`가 지난 `IN_PROGRESS` 요청만 자동 완료

### 5. N+1 쿼리 문제 (중간) ⚠️

**위치**: 
- `CareRequestConverter.toDTO()` (라인 22-51)
- `CareRequestRepository`의 일부 쿼리

**현재 상태**:
- `findByIdWithPet()`는 펫 정보를 JOIN FETCH로 가져옴 ✅
- `findAllActiveRequests()`는 사용자와 펫 정보를 JOIN FETCH로 가져옴 ✅
- 하지만 `CareRequestConverter.toDTO()`에서 `request.getApplications()` 호출 시 LAZY 로딩으로 인한 N+1 쿼리 발생 가능

**실제 코드**:
```java
// CareRequestConverter.java
public CareRequestDTO toDTO(CareRequest request) {
    // ...
    // 지원 정보 추가
    if (request.getApplications() != null && !request.getApplications().isEmpty()) {
        builder.applications(request.getApplications().stream()
                .map(careApplicationConverter::toDTO)
                .collect(Collectors.toList()));
    }
    // ...
}
```

**문제점**:
- `findAllActiveRequests()`에서 `applications`를 JOIN FETCH하지 않음
- 리스트 조회 시 각 요청마다 `applications` 조회 쿼리가 추가로 실행될 수 있음

**해결 방안**:
```java
// CareRequestRepository.java에 추가
@Query("SELECT cr FROM CareRequest cr " +
       "LEFT JOIN FETCH cr.user u " +
       "LEFT JOIN FETCH cr.pet " +
       "LEFT JOIN FETCH cr.applications " +
       "WHERE cr.isDeleted = false AND u.isDeleted = false " +
       "AND u.status = 'ACTIVE' " +
       "ORDER BY cr.createdAt DESC")
List<CareRequest> findAllActiveRequestsWithRelations();

// 또는 @EntityGraph 사용
@EntityGraph(attributePaths = {"user", "pet", "applications", "applications.provider"})
@Query("SELECT cr FROM CareRequest cr " +
       "WHERE cr.isDeleted = false " +
       "AND cr.user.isDeleted = false " +
       "AND cr.user.status = 'ACTIVE' " +
       "ORDER BY cr.createdAt DESC")
List<CareRequest> findAllActiveRequestsWithRelations();
```

### 6. 펫 소유권 검증 부족 (낮음) ⚠️

**위치**: 
- `CareRequestService.createCareRequest()` (라인 89-97)
- `CareRequestService.updateCareRequest()` (라인 117-128)

**현재 상태**:
- 펫 소유자 확인은 있음 ✅
- 하지만 **펫이 삭제되었는지 확인하지 않음**

**실제 코드**:
```java
if (dto.getPetIdx() != null) {
    Pet pet = petRepository.findById(dto.getPetIdx())
            .orElseThrow(() -> new RuntimeException("Pet not found"));
    // 펫 소유자 확인
    if (!pet.getUser().getIdx().equals(user.getIdx())) {
        throw new RuntimeException("펫 소유자만 펫 정보를 연결할 수 있습니다.");
    }
    builder.pet(pet);
}
```

**해결 방안**:
```java
if (dto.getPetIdx() != null) {
    Pet pet = petRepository.findById(dto.getPetIdx())
            .orElseThrow(() -> new RuntimeException("Pet not found"));
    
    // 펫 삭제 여부 확인 추가
    if (Boolean.TRUE.equals(pet.getIsDeleted())) {
        throw new RuntimeException("삭제된 펫은 연결할 수 없습니다.");
    }
    
    // 펫 소유자 확인
    if (!pet.getUser().getIdx().equals(user.getIdx())) {
        throw new RuntimeException("펫 소유자만 펫 정보를 연결할 수 있습니다.");
    }
    
    builder.pet(pet);
}
```

### 7. 상태 전이 검증 없음 (중간) ⚠️

**위치**: `CareRequestService.updateStatus()` (라인 154-163)

**현재 상태**:
- 잘못된 상태 전이가 가능함
- 예: `COMPLETED` → `OPEN`, `CANCELLED` → `IN_PROGRESS` 등

**실제 코드**:
```java
@Transactional
public CareRequestDTO updateStatus(Long idx, String status) {
    CareRequest request = careRequestRepository.findById(idx)
            .orElseThrow(() -> new RuntimeException("CareRequest not found"));
    
    // 상태 전이 검증 없이 바로 변경
    request.setStatus(CareRequestStatus.valueOf(status));
    // ...
}
```

**해결 방안**:
```java
private void validateStatusTransition(CareRequestStatus currentStatus, CareRequestStatus newStatus) {
    // 같은 상태로 변경은 허용
    if (currentStatus == newStatus) {
        return;
    }
    
    Map<CareRequestStatus, List<CareRequestStatus>> allowedTransitions = Map.of(
        CareRequestStatus.OPEN, List.of(
            CareRequestStatus.IN_PROGRESS, 
            CareRequestStatus.CANCELLED
        ),
        CareRequestStatus.IN_PROGRESS, List.of(
            CareRequestStatus.COMPLETED, 
            CareRequestStatus.CANCELLED
        ),
        CareRequestStatus.COMPLETED, List.of(), // 완료된 요청은 변경 불가
        CareRequestStatus.CANCELLED, List.of()  // 취소된 요청은 변경 불가
    );
    
    List<CareRequestStatus> allowed = allowedTransitions.get(currentStatus);
    if (allowed == null || !allowed.contains(newStatus)) {
        throw new RuntimeException(
            String.format("상태 전이가 불가능합니다: %s -> %s", currentStatus, newStatus));
    }
}

@Transactional
public CareRequestDTO updateStatus(Long idx, String status, Long currentUserId) {
    CareRequest request = careRequestRepository.findById(idx)
            .orElseThrow(() -> new RuntimeException("CareRequest not found"));
    
    // 권한 확인 (1.2 참고)
    // ...
    
    // 상태 전이 검증
    CareRequestStatus newStatus = CareRequestStatus.valueOf(status);
    validateStatusTransition(request.getStatus(), newStatus);
    
    request.setStatus(newStatus);
    CareRequest updated = careRequestRepository.save(request);
    return careRequestConverter.toDTO(updated);
}
```

### 8. 삭제된 사용자의 요청 처리 (낮음) ℹ️

**현재 상태**:
- `findAllActiveRequests()` 쿼리에서 `u.isDeleted = false AND u.status = 'ACTIVE'` 조건으로 필터링
- 삭제된 사용자의 요청은 조회되지 않음 ✅
- 하지만 이미 진행 중인 케어 요청(`IN_PROGRESS`)이 있다면 문제가 될 수 있음

**실제 코드**:
```java
@Query("SELECT cr FROM CareRequest cr JOIN FETCH cr.user u LEFT JOIN FETCH cr.pet " +
       "WHERE cr.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' " +
       "ORDER BY cr.createdAt DESC")
List<CareRequest> findAllActiveRequests();
```

**영향**:
- 사용자가 삭제되면 진행 중인 케어 요청도 조회되지 않음
- 제공자가 진행 중인 케어를 확인할 수 없음

**해결 방안** (선택사항):
- 진행 중인 케어 요청(`IN_PROGRESS`, `COMPLETED`)은 사용자 삭제 여부와 관계없이 조회 가능하도록
- 또는 사용자 삭제 시 진행 중인 케어 요청 상태 확인 후 경고

---

## 우선순위별 해결 권장사항

### 즉시 해결 필요 (심각) 🔴
1. ✅ **권한 검증 추가** (수정/삭제/상태 변경)
   - `updateCareRequest()`: 작성자 확인 추가
   - `deleteCareRequest()`: 작성자 확인 추가
   - `updateStatus()`: 작성자 또는 승인된 제공자 확인 추가

### 단기 해결 필요 (중간) 🟡
2. ✅ **동시성 문제 해결**
   - 여러 지원 동시 승인 방지 (다른 ACCEPTED 지원 자동 REJECTED 처리)
3. ✅ **상태 전이 검증 추가**
   - 잘못된 상태 변경 방지
4. ✅ **N+1 쿼리 최적화**
   - `applications` JOIN FETCH 추가
5. ✅ **스케줄러 로직 개선**
   - `IN_PROGRESS` 상태는 자동 완료하지 않도록

### 중기 개선 (낮음) 🟢
6. ✅ **펫 소유권 검증 강화**
   - 삭제된 펫 연결 방지
7. ✅ **삭제된 사용자 요청 처리**
   - 진행 중인 케어 요청 조회 정책 수립

---

## 참고사항

### 정상 동작하는 기능 ✅
- CareApplication 관리: `ConversationService.confirmCareDeal()`에서 정상 동작
- 이메일 인증 확인: 요청 생성 시 확인됨
- 펫 소유자 확인: 기본 검증은 있음
- Soft Delete: 요청 및 댓글 삭제 시 적용됨
- 스케줄러: 날짜 지난 요청 자동 완료 (단, IN_PROGRESS 처리 개선 필요)

### 개선이 필요한 기능 ⚠️
- 권한 검증: 모든 수정/삭제/상태 변경에 필요
- 동시성 제어: 여러 지원 승인 방지
- 상태 전이 검증: 잘못된 상태 변경 방지
- 쿼리 최적화: N+1 문제 해결
