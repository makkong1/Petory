# LocationService 필드 최적화 분석

## 데이터 분석 결과

### 1. 중복/불필요한 필드

#### 🔴 **address vs detailAddress (중복)**
- **현황**: 두 값이 같은 경우가 많음 (도로명주소만 있는 경우)
- **문제**: 
  - `address`: 도로명주소 우선, 없으면 지번주소
  - `detailAddress`: 도로명주소
  - 실제로는 같은 값이 들어가는 경우가 많음
- **제안**: 
  - `detailAddress`는 도로명주소가 있을 때만 저장
  - `address`와 같으면 NULL로 저장하여 중복 방지

#### 🟡 **category vs category3 (중복 가능)**
- **현황**: category는 category3 우선 사용, 실제로 같은 값인 경우가 많음
- **문제**: 중복 저장
- **제안**: 
  - 하위 호환성을 위해 유지 (필수)
  - 저장 시 category3와 같으면 category에 저장하지 않고 NULL로 두고, 조회 시 category3 사용

#### 🟡 **ri (리) 필드**
- **현황**: 대부분 NULL (99% 이상)
- **문제**: 사용 빈도가 매우 낮음
- **제안**: 
  - 필드는 유지하되, NULL인 경우가 많으므로 nullable로 유지
  - 프론트엔드에서 표시하지 않음

#### 🟡 **bunji (번지)**
- **현황**: "702-10 번지" 형식
- **문제**: 주소에 이미 포함될 수 있음
- **제안**: 
  - 상세 주소 정보가 필요한 경우에만 사용
  - 일반적으로는 address 필드만으로 충분

#### 🟡 **buildingNumber (건물 번호)**
- **현황**: "1084 번" 형식
- **문제**: 도로명주소에 이미 포함됨
- **제안**: 
  - 상세 검색/필터링이 필요한 경우에만 사용
  - 일반적으로는 address 필드만으로 충분

#### 🟡 **description**
- **현황**: 간단한 값만 있음 (예: "일반동물병원")
- **문제**: category와 중복될 수 있음
- **제안**: 
  - 더 상세한 설명이 필요한 경우에만 사용
  - 간단한 값은 저장하지 않음

#### 🔴 **coordinates (POINT 타입)**
- **현황**: 엔티티에서 주석 처리됨, `?`로 표시
- **문제**: 
  - latitude, longitude로 대체 가능
  - Spatial Index는 필요하지만 현재 미사용
- **제안**: 
  - 현재는 latitude/longitude 사용
  - Spatial Index가 필요하면 coordinates 필드 활성화

#### 🔴 **zipCode 타입 문제**
- **현황**: 소수점 값으로 저장됨 (47596.0)
- **문제**: 문자열이어야 하는데 숫자로 저장됨
- **제안**: 
  - CSV 임포트 시 문자열로 변환
  - DB 타입 확인 및 수정

### 2. 정상적인 필드 (유지 필요)

#### ✅ **indoor와 outdoor 둘 다 true**
- **설명**: 실내/실외 둘 다 가능한 경우 정상
- **조치**: 유지

#### ✅ **rating NULL**
- **설명**: 리뷰가 없으면 NULL인 것이 정상
- **조치**: 유지

#### ✅ **category 필드**
- **설명**: 하위 호환성을 위해 필수
- **조치**: 유지

### 3. 최적화 제안

#### 즉시 적용 가능한 최적화

1. **detailAddress 중복 제거**
   ```java
   // 저장 시
   String detailAddress = dto.getRoadAddress();
   if (detailAddress != null && detailAddress.equals(address)) {
       detailAddress = null; // 중복 제거
   }
   ```

2. **zipCode 타입 수정**
   ```java
   // CSV 임포트 시
   String zipCode = dto.getZipCode();
   if (zipCode != null) {
       zipCode = zipCode.replace(".0", "").trim(); // 소수점 제거
   }
   ```

3. **description 간단한 값 제거**
   ```java
   // 저장 시
   String description = dto.getDescription();
   if (description != null && description.equals(category)) {
       description = null; // category와 같으면 제거
   }
   ```

#### 장기 최적화 (선택적)

1. **ri, bunji, buildingNumber 필드**
   - 사용 빈도가 낮으므로 선택적 필드로 유지
   - 프론트엔드에서 표시하지 않음

2. **coordinates 필드 활성화**
   - Spatial Index가 필요하면 활성화
   - 현재는 latitude/longitude로 충분

### 4. 필드 사용 빈도 분석

#### 필수 필드 (항상 사용)
- idx, name, category, address, latitude, longitude
- phone, website (있는 경우)
- petFriendly, rating

#### 자주 사용되는 필드
- category1, category2, category3
- sido, sigungu, eupmyeondong
- operatingHours, closedDay
- parkingAvailable, priceInfo

#### 가끔 사용되는 필드
- detailAddress (도로명주소가 지번주소와 다를 때)
- petSize, petRestrictions, petExtraFee
- indoor, outdoor

#### 거의 사용되지 않는 필드
- ri (리) - 99% NULL
- bunji (번지) - 주소에 포함됨
- buildingNumber - 주소에 포함됨
- description - category와 중복

### 5. 권장 사항

1. **즉시 적용**: detailAddress 중복 제거, zipCode 타입 수정
2. **단기**: description 간단한 값 제거
3. **장기**: ri, bunji, buildingNumber는 선택적 필드로 유지하되 프론트엔드에서 표시하지 않음

