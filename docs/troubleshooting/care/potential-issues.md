# 펫케어 서비스 트러블슈팅 가이드

> **참고**: 이 문서는 `docs/domains/care.md`와 실제 백엔드 로직을 대조하여 작성되었습니다.

## 실제로 확인된 문제점들

### 1. 권한 검증 부재 (해결 완료) ✅

#### 1.1 CareRequest 수정/삭제 권한 검증 없음
**상태**: ✅ **해결 완료**

**수정 내용**:
- `CareRequestService.updateCareRequest()`: 작성자 확인 추가
- `CareRequestService.deleteCareRequest()`: 작성자 확인 추가
- `CareRequestService.updateStatus()`: 작성자 또는 승인된 제공자 확인 추가
- 관리자는 권한 검증 우회 가능

---

## 실제로 문제가 될 수 있는 부분 (검토 필요)

### 1. 스케줄러 로직 - IN_PROGRESS 자동 완료

**위치**: `CareRequestScheduler.updateExpiredCareRequests()` (라인 34-60)

**현재 동작**:
- 날짜가 지난 `OPEN` 또는 `IN_PROGRESS` 상태의 요청을 모두 `COMPLETED`로 변경

**잠재적 문제**:
- `IN_PROGRESS` 상태는 실제로 진행 중일 수 있음 (예: 며칠간의 장기 케어)
- `date` 필드가 케어 시작일인지 종료일인지 명확하지 않음
- 자동으로 `COMPLETED`로 변경하면 사용자가 수동으로 완료 처리할 수 없음

**실제 영향도**: 
- **낮음** - `date` 필드가 케어 예정일이라면, 날짜가 지났다는 것은 케어가 끝났다는 의미일 수 있음
- 하지만 장기 케어의 경우 문제가 될 수 있음

**권장사항**:
- `IN_PROGRESS` 상태는 자동 완료하지 않거나
- `startDate`와 `endDate`를 분리하여 관리

---

### 2. N+1 쿼리 가능성

**위치**: 
- `CareRequestConverter.toDTO()` (라인 44-48)
- `CareRequestRepository.findAllActiveRequests()` (라인 23)

**현재 상태**:
- `findAllActiveRequests()`는 `user`와 `pet`만 JOIN FETCH
- `applications`는 JOIN FETCH하지 않음
- `toDTO()`에서 `request.getApplications()` 호출 시 LAZY 로딩으로 추가 쿼리 발생 가능

**실제 영향도**:
- **중간** - 리스트 조회 시 각 요청마다 `applications` 조회 쿼리가 추가로 실행될 수 있음
- 하지만 `applications`를 항상 조회하는 것은 아니므로, 실제 사용 패턴에 따라 다름

**권장사항**:
- `applications`를 자주 조회하는 경우 JOIN FETCH 추가
- 필요 시에만 조회하도록 별도 메서드 제공

---

### 3. 상태 전이 검증 없음

**위치**: `CareRequestService.updateStatus()` (라인 183-205)

**현재 상태**:
- 잘못된 상태 전이가 가능함
- 예: `COMPLETED` → `OPEN`, `CANCELLED` → `IN_PROGRESS` 등

**실제 영향도**:
- **낮음** - 프론트엔드에서 올바른 상태만 전달한다면 문제 없음
- 하지만 API를 직접 호출하면 잘못된 상태 변경 가능

**권장사항**:
- 상태 전이 규칙 정의 및 검증 로직 추가 (선택사항)

---

## 정상 동작하는 기능들 ✅

### 1. CareApplication 관리
- ✅ `ConversationService.confirmCareDeal()`에서 정상 동작
- ✅ 양쪽 모두 거래 확정 시 자동으로 CareApplication 생성 및 ACCEPTED 상태로 설정
- ✅ 기존 CareApplication이 있으면 승인 상태로 변경
- ✅ 같은 providerId로 필터링하므로 중복 지원 방지됨

### 2. 동시성 제어
- ✅ `@Transactional`로 트랜잭션 보장
- ✅ 같은 provider와의 거래 확정은 기존 CareApplication 재사용
- ⚠️ 다른 provider와 동시에 거래 확정하면 여러 ACCEPTED가 생길 수 있음 (하지만 채팅방이 다르므로 실제로는 발생하기 어려움)

### 3. 이메일 인증
- ✅ 요청 생성 시 이메일 인증 확인
- ✅ `EmailVerificationRequiredException` 처리

### 4. 펫 소유자 확인
- ✅ 기본 검증은 있음
- ⚠️ 삭제된 펫 확인은 없음 (낮은 우선순위)

### 5. Soft Delete
- ✅ 요청 및 댓글 삭제 시 Soft Delete 적용
- ✅ 삭제된 요청은 조회되지 않음

---

## 결론

펫케어 도메인은 **전반적으로 잘 설계되어 있으며**, 실제로 트러블슈팅이 발생할 만한 심각한 문제는 **권한 검증 부재** 정도였고, 이는 이미 해결되었습니다.

나머지는:
- **스케줄러**: 실제 사용 패턴에 따라 문제가 될 수도, 안 될 수도 있음
- **N+1 쿼리**: 성능 최적화 관점에서 개선 가능
- **상태 전이**: 방어적 프로그래밍 관점에서 추가 가능

**실제 운영 중 문제가 발생하면 그때 대응하는 것도 충분히 합리적인 접근**입니다.
