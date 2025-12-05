# 위치서비스 공공데이터 CSV 배치 임포트 구현

## 개요

공공데이터 포털에서 제공하는 반려동물 관련 시설 정보 CSV 파일(약 7만건)을 배치로 임포트하여 `LocationService` 엔티티로 저장하는 기능을 구현했습니다.

## 구현 배경

- **데이터 소스**: 공공데이터 포털의 반려동물 관련 시설 정보 (약 70,000건)
- **데이터 형식**: CSV 파일 (31개 필드)
- **요구사항**: 
  - 대용량 데이터를 효율적으로 처리
  - 중복 데이터 방지
  - 에러 발생 시에도 부분 저장 가능
  - 진행 상황 모니터링

## 시스템 아키텍처

### 전체 흐름

```
[프론트엔드] 
    ↓ (CSV 파일 업로드)
[LocationServiceAdminController]
    ↓ (MultipartFile)
[PublicDataLocationService]
    ↓ (CSV 파싱 → DTO 변환 → 엔티티 변환)
[LocationServiceRepository]
    ↓ (배치 저장)
[MySQL Database]
```

### 주요 컴포넌트

#### 1. LocationServiceAdminController
- **역할**: REST API 엔드포인트 제공
- **엔드포인트**: 
  - `POST /api/admin/location-services/import-public-data` (파일 업로드)
  - `POST /api/admin/location-services/import-public-data-path` (파일 경로)

#### 2. PublicDataLocationService
- **역할**: CSV 파일 파싱 및 배치 저장 로직
- **주요 메서드**:
  - `importFromCsv(MultipartFile file)`: 파일 업로드 방식
  - `importFromCsv(String csvFilePath)`: 파일 경로 방식
  - `saveBatch(List<LocationService> batch)`: 배치 저장 (별도 트랜잭션)

#### 3. PublicDataLocationDTO
- **역할**: CSV 라인을 파싱하여 Java 객체로 변환

## 구현 로직

### 1. CSV 파일 파싱

```java
// CSV 필드 파싱 (쉼표 구분, 따옴표 처리)
private List<String> parseCsvFields(String line) {
    List<String> fields = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;

    for (int i = 0; i < line.length(); i++) {
        char c = line.charAt(i);
        if (c == '"') {
            inQuotes = !inQuotes;
        } else if (c == ',' && !inQuotes) {
            fields.add(current.toString().trim());
            current = new StringBuilder();
        } else {
            current.append(c);
        }
    }
    fields.add(current.toString().trim()); // 마지막 필드
    return fields;
}
```

**특징**:
- 따옴표로 감싸진 필드 내부의 쉼표 처리
- UTF-8 인코딩 지원

### 2. 데이터 검증

```java
private boolean isValid(PublicDataLocationDTO dto) {
    // 최소한 시설명과 주소 중 하나는 있어야 함
    if (!StringUtils.hasText(dto.getFacilityName())) {
        return false;
    }
    if (!StringUtils.hasText(dto.getRoadAddress()) && 
        !StringUtils.hasText(dto.getJibunAddress())) {
        return false;
    }
    return true;
}
```

### 3. 중복 체크

**2단계 중복 체크**:
1. **메모리 내 중복 체크**: 현재 임포트 세션 내에서 중복 확인
   ```java
   Set<String> deduplicationKeys = new HashSet<>();
   String dedupKey = buildDedupKey(dto); // "시설명|주소" 형식
   ```
2. **DB 중복 체크**: 기존 DB 데이터와 중복 확인
   ```java
   locationServiceRepository.existsByNameAndDetailAddress(...)
   locationServiceRepository.existsByNameAndAddress(...)
   ```

### 4. 엔티티 변환 전략

**핵심 원칙**: 모든 값 검증 및 파싱을 먼저 수행한 후, 마지막에 엔티티 생성

```java
private LocationService convertToEntity(PublicDataLocationDTO dto) {
    // ============================================
    // 1단계: 모든 값 검증 및 파싱 (엔티티 생성 전)
    // ============================================
    Double latitude = parseDouble(dto.getLatitude());
    Double longitude = parseDouble(dto.getLongitude());
    LocalDate lastUpdated = parseDate(dto.getLastUpdatedDate());
    Boolean petFriendly = parseBoolean(dto.getPetFriendly());
    // ... 기타 필드 파싱

    // ============================================
    // 2단계: 엔티티 생성 (모든 검증/파싱 완료 후)
    // ============================================
    return LocationService.builder()
        .name(dto.getFacilityName())
        .latitude(latitude)
        .longitude(longitude)
        // ... 기타 필드 설정
        .build();
}
```

**이유**: 파싱 실패 시 영속성 컨텍스트에 엔티티가 들어가지 않도록 방지

### 5. 배치 저장 전략

**배치 크기**: 1000개씩 청크 단위로 저장

```java
private static final int BATCH_SIZE = 1000;

// 배치 사이즈에 도달하면 저장
if (batch.size() >= BATCH_SIZE) {
    int batchSaved = saveBatch(batch);
    saved += batchSaved;
    batch.clear();
    entityManager.clear(); // 세션 정리
}
```

**트랜잭션 분리**: 각 배치를 별도 트랜잭션으로 처리

```java
@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
private int saveBatch(List<LocationService> batch) {
    try {
        locationServiceRepository.saveAll(batch);
        return batch.size();
    } catch (Exception e) {
        // 배치 저장 실패 시 개별 저장 시도
        entityManager.clear();
        // ... 개별 저장 로직
    }
}
```

**장점**:
- 한 배치가 실패해도 다른 배치는 정상 저장
- 트랜잭션 롤백 전용 표시 문제 방지
- 메모리 효율성 (세션 정리)

## 발생한 오류 및 해결 방법

### 오류 1: 파일 업로드 크기 제한 초과

**오류 메시지**:
```
org.springframework.web.multipart.MaxUploadSizeExceededException: Maximum upload size exceeded
```

**원인**: Spring Boot 기본 파일 업로드 크기 제한 (1MB 또는 10MB)

**해결 방법**:
```properties
# application.properties
spring.servlet.multipart.enabled=true
spring.servlet.multipart.max-file-size=500MB
spring.servlet.multipart.max-request-size=500MB
```

### 오류 2: created_at, updated_at 컬럼 없음

**오류 메시지**:
```
Unknown column 'created_at' in 'field list'
```

**원인**: 엔티티에 `created_at`, `updated_at` 필드가 있지만 DB에는 컬럼이 없음

**해결 방법**: 엔티티에서 해당 필드 주석 처리
```java
// created_at, updated_at는 DB에 없으므로 주석 처리
// @Column(name = "created_at", nullable = false, updatable = false)
// private LocalDateTime createdAt;
```

**판단 근거**:
- `last_updated` 필드로 데이터 갱신 시점 추적 가능
- `data_source` 필드로 데이터 출처 구분 가능
- 리뷰는 별도 테이블에서 시간 정보 관리

### 오류 3: coordinates 컬럼 NOT NULL 문제

**오류 메시지**:
```
Field 'coordinates' doesn't have a default value
```

**원인**: DB의 `coordinates` 컬럼이 NOT NULL로 설정되어 있지만 엔티티에서 주석 처리됨

**해결 방법**: DB에서 NULL 허용으로 변경
```sql
ALTER TABLE locationservice MODIFY COLUMN coordinates POINT NULL;
```

### 오류 4: Hibernate 세션 오염 (null identifier)

**오류 메시지**:
```
Entry for instance of 'com.linkup.Petory.domain.location.entity.LocationService' 
has a null identifier (this can happen if the session is flushed after an exception occurs)
```

**원인**: 
- 배치 저장 중 예외 발생 후 세션이 오염됨
- `convertToEntity`에서 엔티티 생성 중 예외 발생 시 영속성 컨텍스트에 추가됨

**해결 방법**:

1. **엔티티 변환 예외 처리 강화**:
```java
LocationService entity;
try {
    entity = convertToEntity(dto);
    if (entity == null || entity.getIdx() != null) {
        skipped++;
        continue;
    }
} catch (Exception e) {
    error++;
    entityManager.clear(); // 세션 정리
    continue;
}
```

2. **세션 정리 강화**:
```java
// 배치 저장 후 세션 정리
entityManager.clear();

// 예외 발생 시에도 세션 정리
catch (Exception e) {
    entityManager.clear();
}
```

3. **엔티티 변환 구조 개선**: 모든 파싱을 먼저 수행한 후 엔티티 생성

### 오류 5: 트랜잭션 롤백 문제

**오류 메시지**:
```
Transaction silently rolled back because it has been marked as rollback-only
```

**원인**: 
- 배치 저장 중 예외 발생으로 트랜잭션이 rollback-only로 표시됨
- 예외를 catch하여 계속 진행하려고 하면 롤백 전용 트랜잭션을 커밋할 수 없음

**해결 방법**:

1. **메인 메서드 트랜잭션 제거**:
```java
// @Transactional 제거
public BatchImportResult importFromCsv(MultipartFile file) {
    // ...
}
```

2. **배치 저장을 별도 트랜잭션으로 분리**:
```java
@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
private int saveBatch(List<LocationService> batch) {
    // 각 배치가 독립적인 트랜잭션으로 처리
}
```

**효과**:
- 한 배치가 실패해도 다른 배치는 정상 저장
- 트랜잭션 롤백 전용 표시 문제 방지

## 데이터 매핑

### CSV 필드 → DTO → 엔티티

| CSV 필드 | DTO 필드 | 엔티티 필드 | 비고 |
|---------|---------|-----------|------|
| 시설명 | facilityName | name | 필수 |
| 카테고리1 | category1 | category1 | |
| 카테고리2 | category2 | category2 | |
| 카테고리3 | category3 | category3, category | category3 우선 |
| 시도명칭 | sidoName | sido | |
| 시군구명칭 | sigunguName | sigungu | |
| 법정읍면동명칭 | eupmyeondongName | eupmyeondong | |
| 리명칭 | riName | ri | |
| 번지 | bunji | bunji | |
| 도로명이름 | roadName | roadName | |
| 건물번호 | buildingNumber | buildingNumber | |
| 위도 | latitude | latitude | Double 파싱 |
| 경도 | longitude | longitude | Double 파싱 |
| 우편번호 | postalCode | zipCode | |
| 도로명주소 | roadAddress | detailAddress, address | 우선 사용 |
| 지번주소 | jibunAddress | address | 도로명주소 없을 때 |
| 전화번호 | phone | phone | |
| 홈페이지 | website | website | |
| 휴무일 | closedDays | closedDay | |
| 운영시간 | operatingHours | operatingHours | 문자열 그대로 |
| 주차가능여부 | parkingAvailable | parkingAvailable | Y/N → Boolean |
| 입장가격정보 | entranceFee | priceInfo | |
| 반려동물동반가능정보 | petFriendly | petFriendly | Y/N → Boolean |
| 반려동물전용정보 | petOnly | isPetOnly | Y/N → Boolean |
| 입장가능동물크기 | petSizeLimit | petSize | |
| 반려동물제한사항 | petRestrictions | petRestrictions | |
| 장소실내여부 | indoor | indoor | Y/N → Boolean |
| 장소실외여부 | outdoor | outdoor | Y/N → Boolean |
| 기본정보장소설명 | description | description | |
| 애견동반추가요금 | petAdditionalFee | petExtraFee | |
| 최종작성일 | lastUpdatedDate | lastUpdated | yyyy-MM-dd 파싱 |

### 데이터 타입 변환

```java
// Boolean 변환 (Y/N → true/false)
private Boolean parseBoolean(String value) {
    if (!StringUtils.hasText(value)) return null;
    return "Y".equalsIgnoreCase(value.trim());
}

// Double 변환
private Double parseDouble(String value) {
    if (!StringUtils.hasText(value)) return null;
    try {
        return Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
        return null;
    }
}

// 날짜 변환 (yyyy-MM-dd)
private LocalDate parseDate(String value) {
    if (!StringUtils.hasText(value)) return null;
    try {
        return LocalDate.parse(value.trim(), DATE_FORMATTER);
    } catch (DateTimeParseException e) {
        return null;
    }
}
```

## 성능 최적화

### 1. 배치 저장
- **배치 크기**: 1000개씩 청크 단위
- **효과**: 트랜잭션 오버헤드 최소화

### 2. 메모리 관리
- **세션 정리**: 각 배치 저장 후 `entityManager.clear()` 호출
- **효과**: 메모리 사용량 감소, 세션 오염 방지

### 3. 중복 체크 최적화
- **메모리 내 중복 체크**: Set을 사용한 O(1) 조회
- **DB 중복 체크**: Repository 메서드 활용 (인덱스 활용)

### 4. 트랜잭션 분리
- **별도 트랜잭션**: 각 배치를 `REQUIRES_NEW`로 분리
- **효과**: 일부 실패해도 다른 배치는 저장됨

## 결과 통계

### BatchImportResult

```java
public static class BatchImportResult {
    private int totalRead;      // 총 읽은 라인 수
    private int saved;          // 저장된 개수
    private int duplicate;      // 중복으로 스킵된 개수
    private int skipped;        // 검증 실패로 스킵된 개수
    private int error;          // 에러 발생 개수
}
```

### 예시 결과

```
총 읽음: 70,650
저장: 23,919
중복: 46,726
스킵: 0
에러: 5
```

## 프론트엔드 구현

### LocationServiceManagementSection.js

```javascript
const handleImport = async () => {
  if (!selectedFile) {
    alert('CSV 파일을 선택해주세요.');
    return;
  }

  setLoading(true);
  try {
    const response = await locationServiceApi.importPublicData(selectedFile);
    setResult(response.data);
    alert(`임포트 완료!\n총 읽음: ${response.data.totalRead}\n저장: ${response.data.saved}`);
  } catch (err) {
    setError(err?.response?.data?.message || err.message);
  } finally {
    setLoading(false);
  }
};
```

### API 호출

```javascript
// locationServiceApi.js
importPublicData: (file) => {
  const formData = new FormData();
  formData.append('file', file);
  return adminApi.post('/import-public-data', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
}
```

## 주의사항

### 1. 파일 인코딩
- **권장**: UTF-8 인코딩
- **주의**: Excel로 저장 시 CP949(EUC-KR)로 저장될 수 있음
- **해결**: Excel에서 "CSV UTF-8(쉼표로 분리)(*.csv)" 형식으로 저장

### 2. 파일 크기
- **제한**: 500MB (application.properties에서 설정)
- **대용량 파일**: 배치 처리로 메모리 효율적으로 처리

### 3. 트랜잭션 관리
- **메인 메서드**: 트랜잭션 없음
- **배치 저장**: 각 배치가 별도 트랜잭션
- **효과**: 일부 실패해도 다른 배치는 저장됨

### 4. 세션 관리
- **배치 저장 후**: `entityManager.clear()` 호출 필수
- **예외 발생 시**: 즉시 세션 정리
- **효과**: 세션 오염 방지, 메모리 관리

## 향후 개선 사항

1. **진행 상황 모니터링**: WebSocket을 통한 실시간 진행률 표시
2. **재시도 로직**: 실패한 배치 자동 재시도
3. **인코딩 자동 감지**: 파일 인코딩 자동 감지 및 변환
4. **증분 업데이트**: 기존 데이터 업데이트 로직 추가
5. **병렬 처리**: 여러 배치를 병렬로 처리하여 성능 향상

## 참고 파일

- **서비스**: `backend/main/java/com/linkup/Petory/domain/location/service/PublicDataLocationService.java`
- **컨트롤러**: `backend/main/java/com/linkup/Petory/domain/location/controller/LocationServiceAdminController.java`
- **DTO**: `backend/main/java/com/linkup/Petory/domain/location/dto/PublicDataLocationDTO.java`
- **프론트엔드**: `frontend/src/components/Admin/sections/LocationServiceManagementSection.js`
- **API**: `frontend/src/api/locationServiceApi.js`

## 요약

위치서비스 공공데이터 CSV 배치 임포트 기능은 대용량 데이터를 효율적으로 처리하기 위해 다음과 같은 전략을 사용합니다:

1. **배치 저장**: 1000개씩 청크 단위로 저장
2. **트랜잭션 분리**: 각 배치를 별도 트랜잭션으로 처리
3. **세션 관리**: 배치 저장 후 세션 정리로 메모리 효율성 확보
4. **에러 처리**: 예외 발생 시에도 다른 배치는 정상 저장
5. **중복 방지**: 메모리 내 중복 체크 + DB 중복 체크

이를 통해 약 7만건의 대용량 데이터를 안정적으로 처리할 수 있습니다.

