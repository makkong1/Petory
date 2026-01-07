# 펫케어 요청 조회 시퀀스 다이어그램

## 포트폴리오용 간결 버전

### 최적화 전 (N+1 문제 발생)

```javascript
// 시퀀스 다이어그램 (최적화 전)
const beforeOptimizationSequence = `sequenceDiagram
    participant User as 사용자
    participant Frontend as Frontend
    participant Service as CareRequestService
    participant Repo as CareRequestRepository
    participant Converter as CareRequestConverter
    participant PetConverter as PetConverter
    participant FileService as AttachmentFileService
    participant DB as MySQL
    
    User->>Frontend: GET /api/care-requests
    Frontend->>Service: getAllCareRequests()
    Service->>Repo: findAllActiveRequests()
    Repo->>DB: 메인 쿼리 (CareRequest, User, Pet) (1)
    DB-->>Repo: 1004개 CareRequest 반환
    
    Note over Service,DB: N+1 문제 발생
    loop 각 CareRequest마다 (1004번)
        Converter->>DB: getApplications() (2, 3, 4...)
        DB-->>Converter: CareApplication 개별 조회
    end
    
    loop 각 Pet마다 (~700번)
        PetConverter->>FileService: getAttachments(PET, petIdx) (5, 6, 7...)
        FileService->>DB: File 개별 조회
        PetConverter->>DB: getVaccinations() (8, 9, 10...)
        DB-->>PetConverter: PetVaccination 개별 조회
    end
    
    Service-->>Frontend: List<CareRequestDTO>
    Frontend-->>User: 펫케어 요청 목록 표시
    
    Note over Service,DB: 총 ~2400개 쿼리 발생`;
```

### 최적화 후 (N+1 문제 해결)

```javascript
// 시퀀스 다이어그램 (최적화 후)
const afterOptimizationSequence = `sequenceDiagram
    participant User as 사용자
    participant Frontend as Frontend
    participant Service as CareRequestService
    participant Repo as CareRequestRepository
    participant Converter as CareRequestConverter
    participant PetConverter as PetConverter
    participant FileService as AttachmentFileService
    participant DB as MySQL
    
    User->>Frontend: GET /api/care-requests
    Frontend->>Service: getAllCareRequests()
    Service->>Repo: findAllActiveRequests()
    Note over Repo,DB: Fetch Join으로 한 번에 조회
    Repo->>DB: 메인 쿼리 (CareRequest, User, Pet, CareApplication) (1)
    DB-->>Repo: 1004개 CareRequest 반환 (applications 포함)
    
    Note over Service,DB: 배치 조회로 최적화
    Converter->>PetConverter: toDTOList(pets)
    PetConverter->>FileService: getAttachmentsBatch(PET, petIndices) (2)
    FileService->>DB: File 배치 조회 (IN 절)
    DB-->>FileService: 모든 Pet의 File 한 번에 조회
    
    Note over PetConverter,DB: @BatchSize로 배치 조회
    PetConverter->>DB: PetVaccination 배치 조회 (3, 4...)
    Note over DB: 50개씩 배치로 조회 (약 20번)
    DB-->>PetConverter: PetVaccination 배치 조회 결과
    
    Converter->>Converter: 미리 변환된 PetDTO 사용
    Service-->>Frontend: List<CareRequestDTO>
    Frontend-->>User: 펫케어 요청 목록 표시
    
    Note over Service,DB: 총 4-5개 쿼리로 감소<br/>99.8% 쿼리 수 감소`;
```

---

## 상세 버전 (문서용)

### 최적화 전 (N+1 문제 발생)

```mermaid
sequenceDiagram
    participant User as 사용자
    participant Frontend as Frontend
    participant Controller as CareRequestController
    participant Service as CareRequestService
    participant Repo as CareRequestRepository
    participant CareConverter as CareRequestConverter
    participant PetConverter as PetConverter
    participant FileService as AttachmentFileService
    participant DB as MySQL
    
    User->>Frontend: GET /api/care-requests
    Frontend->>Controller: getAllCareRequests()
    Controller->>Service: getAllCareRequests(status, location)
    
    Note over Service,Repo: 1. 메인 쿼리 실행
    Service->>Repo: findAllActiveRequests()
    Repo->>DB: SELECT cr, u, p FROM carerequest<br/>JOIN users u ON ...<br/>LEFT JOIN pets p ON ... (쿼리 1)
    DB-->>Repo: CareRequest 리스트 반환 (1004개)
    Repo-->>Service: List<CareRequest>
    
    Service->>CareConverter: toDTOList(requests)
    
    Note over CareConverter,DB: 2. N+1 문제: CareApplication 조회
    loop 각 CareRequest마다 (1004번)
        CareConverter->>CareConverter: toDTO(request)
        CareConverter->>DB: request.getApplications()<br/>LAZY 로딩 트리거
        DB-->>CareConverter: CareApplication 조회 (쿼리 2, 3, 4...)
    end
    
    Note over CareConverter,PetConverter: 3. N+1 문제: Pet 변환
    loop 각 CareRequest마다 (Pet가 있는 경우)
        CareConverter->>PetConverter: toDTO(pet)
        
        Note over PetConverter,DB: 4. N+1 문제: File 조회
        PetConverter->>FileService: getAttachments(PET, petIdx)
        FileService->>DB: SELECT * FROM file<br/>WHERE target_type=? AND target_idx=? (쿼리 5, 6, 7...)
        DB-->>FileService: File 리스트
        FileService-->>PetConverter: List<FileDTO>
        
        Note over PetConverter,DB: 5. N+1 문제: PetVaccination 조회
        PetConverter->>DB: pet.getVaccinations()<br/>LAZY 로딩 트리거
        DB-->>PetConverter: PetVaccination 조회 (쿼리 8, 9, 10...)
        
        PetConverter-->>CareConverter: PetDTO
    end
    
    CareConverter-->>Service: List<CareRequestDTO>
    Service-->>Controller: List<CareRequestDTO>
    Controller-->>Frontend: JSON 응답
    Frontend-->>User: 펫케어 요청 목록 표시
    
    Note over Service,DB: 총 약 2400개 쿼리 발생<br/>- 메인 쿼리: 1개<br/>- CareApplication: ~1004개<br/>- File: ~700개<br/>- PetVaccination: ~700개
```

## 최적화 후 (N+1 문제 해결)

```mermaid
sequenceDiagram
    participant User as 사용자
    participant Frontend as Frontend
    participant Controller as CareRequestController
    participant Service as CareRequestService
    participant Repo as CareRequestRepository
    participant CareConverter as CareRequestConverter
    participant PetConverter as PetConverter
    participant FileService as AttachmentFileService
    participant DB as MySQL
    
    User->>Frontend: GET /api/care-requests
    Frontend->>Controller: getAllCareRequests()
    Controller->>Service: getAllCareRequests(status, location)
    
    Note over Service,Repo: 1. 메인 쿼리 (Fetch Join으로 최적화)
    Service->>Repo: findAllActiveRequests()
    Repo->>DB: SELECT DISTINCT cr, u, p, a FROM carerequest cr<br/>JOIN FETCH cr.user u<br/>LEFT JOIN FETCH cr.pet p<br/>LEFT JOIN FETCH cr.applications a<br/>WHERE ... (쿼리 1)
    Note over DB: CareRequest, User, Pet, CareApplication<br/>한 번에 조회
    DB-->>Repo: CareRequest 리스트 반환 (1004개, applications 포함)
    Repo-->>Service: List<CareRequest>
    
    Service->>CareConverter: toDTOList(requests)
    
    Note over CareConverter,PetConverter: 2. Pet 배치 변환 준비
    CareConverter->>CareConverter: 모든 Pet 수집 및 중복 제거
    CareConverter->>PetConverter: toDTOList(pets)
    
    Note over PetConverter,FileService: 3. File 배치 조회
    PetConverter->>PetConverter: 모든 Pet의 idx 수집
    PetConverter->>FileService: getAttachmentsBatch(PET, petIndices)
    FileService->>DB: SELECT * FROM file<br/>WHERE target_type=? AND target_idx IN (?,?,...) (쿼리 2)
    Note over DB: 모든 Pet의 File을<br/>한 번에 조회
    DB-->>FileService: 모든 Pet의 File 리스트
    FileService-->>PetConverter: Map<petIdx, List<FileDTO>>
    
    Note over PetConverter,DB: 4. PetVaccination 배치 조회 (@BatchSize)
    loop 각 Pet 변환 시
        PetConverter->>PetConverter: toDTO(pet, profileImageUrl)
        PetConverter->>DB: pet.getVaccinations()<br/>@BatchSize 트리거 (최대 50개씩)
        DB-->>PetConverter: PetVaccination 배치 조회 (쿼리 3, 4...)
        Note over DB: 50개씩 배치로 조회<br/>(1004개 / 50 = 약 20번)
    end
    
    PetConverter-->>CareConverter: List<PetDTO>
    
    Note over CareConverter: 5. 미리 변환된 PetDTO 사용
    CareConverter->>CareConverter: Map<petIdx, PetDTO> 생성
    loop 각 CareRequest 변환
        CareConverter->>CareConverter: toDTO(request, petDTO)
        Note over CareConverter: 개별 조회 없이<br/>미리 변환된 데이터 사용
    end
    
    CareConverter-->>Service: List<CareRequestDTO>
    Service-->>Controller: List<CareRequestDTO>
    Controller-->>Frontend: JSON 응답
    Frontend-->>User: 펫케어 요청 목록 표시
    
    Note over Service,DB: 총 4-5개 쿼리로 감소<br/>- 메인 쿼리: 1개 (CareApplication 포함)<br/>- File 배치 조회: 1개<br/>- PetVaccination 배치 조회: 1-2개 (@BatchSize)<br/>- 기타: 1개
```

## 최적화 핵심 포인트

### 1단계: CareApplication N+1 해결
- **문제**: 각 CareRequest마다 `getApplications()` 호출 시 LAZY 로딩으로 개별 쿼리 발생
- **해결**: Repository 쿼리에 `LEFT JOIN FETCH cr.applications` 추가
- **효과**: 1004개 쿼리 → 0개 (메인 쿼리에 포함)

### 2단계: File N+1 해결
- **문제**: 각 Pet마다 `getAttachments()` 호출로 개별 쿼리 발생
- **해결**: `PetConverter.toDTOList()`에서 `getAttachmentsBatch()` 사용
- **효과**: ~700개 쿼리 → 1개 (배치 조회)

### 3단계: PetVaccination N+1 해결
- **문제**: 각 Pet마다 `getVaccinations()` 호출 시 LAZY 로딩으로 개별 쿼리 발생
- **해결**: `@BatchSize(size = 50)` 어노테이션 사용
- **효과**: ~700개 쿼리 → 1-2개 (배치 조회, 50개씩)

## 성능 개선 결과

| 항목 | 최적화 전 | 최적화 후 | 개선율 |
|------|----------|----------|--------|
| **쿼리 수** | ~2400개 | 4-5개 | **99.8% 감소** |
| **실행 시간** | 1084ms | 66ms | **94% 감소** |
| **메모리 사용** | 21MB | 6MB | **71% 감소** |
| **네트워크 왕복** | 2400번 | 4-5번 | **99.8% 감소** |

## 기술 스택

- **Backend**: Spring Boot 3.5.7, JPA/Hibernate
- **Database**: MySQL
- **최적화 기법**: 
  - Fetch Join (LEFT JOIN FETCH)
  - 배치 조회 (Batch Fetching)
  - @BatchSize 어노테이션
  - DISTINCT를 활용한 중복 제거

