# Missing Pet 도메인

## 개요

실종 동물 신고 및 관리 시스템으로, 반려동물을 잃어버린 사용자가 신고하고 다른 사용자들이 정보를 제공할 수 있는 도메인입니다.

## Entity 구조

### MissingPetBoard (실종 동물 게시판)

```java
@Entity
@Table(name = "MissingPetBoard")
public class MissingPetBoard {
    Long idx;                    // PK
    Users user;                  // 신고자 (ManyToOne)
    String title;                // 제목
    String content;              // 상세 내용
    String petName;              // 반려동물 이름
    String species;              // 종 (개, 고양이 등)
    String breed;                // 품종
    MissingPetGender gender;     // 성별
    String age;                  // 나이
    String color;                // 색상/특징
    LocalDate lostDate;          // 실종 날짜
    String lostLocation;         // 실종 장소
    BigDecimal latitude;         // 위도
    BigDecimal longitude;        // 경도
    MissingPetStatus status;     // 상태
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    Boolean isDeleted;
}
```

**연관관계:**
- `ManyToOne` → Users (신고자)
- `OneToMany` → MissingPetComment (댓글)
- `OneToMany` → AttachmentFile (사진)

### MissingPetComment

```java
@Entity
@Table(name = "missing_pet_comment")
public class MissingPetComment {
    Long idx;                    // PK
    MissingPetBoard board;       // 게시글 (ManyToOne)
    Users user;                  // 작성자 (ManyToOne)
    String content;              // 내용 (목격 정보 등)
    LocalDateTime createdAt;
    Boolean isDeleted;
}
```

## Enum 정의

### MissingPetStatus
```java
public enum MissingPetStatus {
    MISSING,      // 실종 중
    FOUND,        // 찾음
    CLOSED        // 종료
}
```

### MissingPetGender
```java
public enum MissingPetGender {
    MALE,         // 수컷
    FEMALE,       // 암컷
    UNKNOWN       // 알 수 없음
}
```

## Service 주요 기능

### MissingPetBoardService

```java
// 실종 신고 생성
MissingPetBoardDTO createMissingPet(MissingPetBoardDTO dto)

// 실종 동물 목록 (페이징, 상태 필터, 위치 기반)
Page<MissingPetBoardDTO> getAllMissingPets(MissingPetStatus status, int page, int size)

// 위치 기반 검색 (반경 내)
List<MissingPetBoardDTO> searchByLocation(double lat, double lng, double radiusKm)

// 실종 동물 상세
MissingPetBoardDTO getMissingPet(long id)

// 실종 신고 수정
MissingPetBoardDTO updateMissingPet(long id, MissingPetBoardDTO dto)

// 찾음 처리
void markAsFound(long id)

// 신고 삭제
void deleteMissingPet(long id)

// 내 실종 신고
List<MissingPetBoardDTO> getMyMissingPets(long userId)
```

## 다른 도메인과의 연관관계

- **User**: 신고자
- **File**: 실종 동물 사진 첨부
- **Notification**: 댓글 작성 시 알림
- **Report**: 부적절한 신고 접수

## 위치 기반 검색

### Haversine 공식 사용

```java
// 두 좌표 간 거리 계산 (km)
private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
    final int R = 6371; // 지구 반경 (km)
    
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
               Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
               Math.sin(dLng / 2) * Math.sin(dLng / 2);
    
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    
    return R * c;
}
```

### Native Query (MySQL Spatial)

```java
@Query(value = 
    "SELECT * FROM MissingPetBoard " +
    "WHERE ST_Distance_Sphere(point(longitude, latitude), point(:lng, :lat)) <= :radiusMeters " +
    "AND status = 'MISSING' AND is_deleted = false " +
    "ORDER BY created_at DESC",
    nativeQuery = true)
List<MissingPetBoard> findByLocationWithin(
    @Param("lat") double lat, 
    @Param("lng") double lng, 
    @Param("radiusMeters") double radiusMeters
);
```

## API 엔드포인트

- `GET /api/missing-pets` - 목록
- `GET /api/missing-pets/{id}` - 상세
- `POST /api/missing-pets` - 신고
- `PUT /api/missing-pets/{id}` - 수정
- `PUT /api/missing-pets/{id}/found` - 찾음 처리
- `DELETE /api/missing-pets/{id}` - 삭제
- `GET /api/missing-pets/nearby` - 위치 기반 검색

## 개선 아이디어

1. **이미지 인식 AI**
   - 사진 업로드 시 유사 동물 자동 검색
   - TensorFlow/OpenCV

2. **푸시 알림**
   - 내 위치 근처 실종 신고 시 알림
   - 백그라운드 위치 추적

3. **지도 시각화**
   - 실종 위치 지도 표시
   - 클러스터링

