# Pet Feature Documentation

## 개요
사용자가 자신의 반려동물 정보를 등록하고 관리할 수 있는 기능입니다. 펫케어 요청, 실종 제보 등에서 재사용됩니다.

## 데이터베이스 구조

### 테이블

#### 1. pets (반려동물 프로필)
- 사용자가 등록한 반려동물의 기본 정보를 저장
- 주요 필드:
  - `pet_name`: 애완동물 이름
  - `pet_type`: 종류 (DOG, CAT, BIRD, RABBIT, HAMSTER, ETC)
  - `breed`: 품종
  - `gender`: 성별 (M, F, UNKNOWN)
  - `age`: 나이 (문자열, 예: "3살", "5개월")
  - `birth_date`: 생년월일
  - `weight`: 몸무게 (DECIMAL(5,2))
  - `is_neutered`: 중성화 여부
  - `health_info`: 건강 정보
  - `special_notes`: 특이사항

#### 2. pet_images (반려동물 프로필 사진)
- 한 애완동물당 여러 이미지 저장 가능
- 주요 필드:
  - `pet_idx`: 펫 ID (FK)
  - `image_url`: 이미지 URL
  - `is_deleted`: 소프트 삭제 플래그

#### 3. pet_vaccinations (반려동물 예방접종 기록)
- 예방접종 이력 관리
- 주요 필드:
  - `pet_idx`: 펫 ID (FK)
  - `vaccine_name`: 백신 이름
  - `vaccinated_at`: 접종일
  - `next_due`: 다음 접종 예정일
  - `notes`: 메모

## 백엔드 구조

### 엔티티 (Entity)
- `Pet.java`: 반려동물 엔티티
- `PetImage.java`: 반려동물 이미지 엔티티
- `PetVaccination.java`: 예방접종 기록 엔티티
- `PetType.java`: 펫 종류 Enum
- `PetGender.java`: 펫 성별 Enum

### DTO
- `PetDTO.java`: 펫 데이터 전송 객체
- `PetImageDTO.java`: 펫 이미지 데이터 전송 객체
- `PetVaccinationDTO.java`: 예방접종 데이터 전송 객체

### Repository
- `PetRepository.java`: 펫 데이터 접근
  - `findByUserIdAndNotDeleted()`: 사용자별 펫 목록 조회
  - `findByPetTypeAndIsDeletedFalse()`: 타입별 펫 조회
- `PetImageRepository.java`: 펫 이미지 데이터 접근
- `PetVaccinationRepository.java`: 예방접종 데이터 접근

### Service
- `PetService.java`: 펫 비즈니스 로직
  - `createPet()`: 펫 생성
  - `getPet()`: 펫 조회
  - `updatePet()`: 펫 수정
  - `deletePet()`: 펫 삭제 (소프트 삭제)
  - `restorePet()`: 펫 복구
  - `getPetsByUserId()`: 사용자별 펫 목록

### Controller
- `PetController.java`: 펫 REST API 엔드포인트
  - `GET /api/pets`: 자신의 펫 목록 조회
  - `GET /api/pets/{petIdx}`: 펫 상세 조회
  - `POST /api/pets`: 펫 생성
  - `PUT /api/pets/{petIdx}`: 펫 수정
  - `DELETE /api/pets/{petIdx}`: 펫 삭제
  - `POST /api/pets/{petIdx}/restore`: 펫 복구
  - `GET /api/pets/type/{petType}`: 타입별 펫 조회

### Converter
- `PetConverter.java`: Pet Entity ↔ PetDTO 변환
- `PetImageConverter.java`: PetImage Entity ↔ PetImageDTO 변환
- `PetVaccinationConverter.java`: PetVaccination Entity ↔ PetVaccinationDTO 변환

## API 엔드포인트

### 펫 목록 조회
```
GET /api/pets
Authorization: Bearer {token}
```

**응답:**
```json
[
  {
    "idx": 1,
    "petName": "뽀삐",
    "petType": "DOG",
    "breed": "골든 리트리버",
    "gender": "M",
    "age": "3살",
    "weight": 25.5,
    "isNeutered": true
  }
]
```

### 펫 생성
```
POST /api/pets
Authorization: Bearer {token}
Content-Type: application/json

{
  "petName": "뽀삐",
  "petType": "DOG",
  "breed": "골든 리트리버",
  "gender": "M",
  "age": "3살",
  "weight": 25.5,
  "isNeutered": true,
  "birthDate": "2021-01-15"
}
```

### 펫 수정
```
PUT /api/pets/{petIdx}
Authorization: Bearer {token}
Content-Type: application/json

{
  "petName": "뽀삐",
  "weight": 26.0
}
```

### 펫 삭제
```
DELETE /api/pets/{petIdx}
Authorization: Bearer {token}
```

## 주요 특징

1. **소프트 삭제**: 펫 삭제 시 실제로 삭제되지 않고 `is_deleted` 플래그만 설정
2. **연관 데이터**: 펫 이미지와 예방접종 기록은 펫과 함께 관리
3. **재사용성**: 펫케어 요청, 실종 제보 등에서 등록된 펫 정보 재사용 가능
4. **인증 필수**: 모든 API는 인증된 사용자만 접근 가능
5. **자신의 펫만 관리**: 사용자는 자신의 펫만 조회/수정/삭제 가능

## 향후 확장 계획

1. 펫 이미지 다중 업로드 API
2. 예방접종 알림 기능
3. 펫 건강 기록 관리
4. 펫케어 요청 시 펫 선택 기능 연동
5. 실종 제보 시 펫 정보 자동 입력 기능

