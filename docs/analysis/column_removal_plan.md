# LocationService 컬럼 삭제 계획

## 사용 현황 분석

### 1. 완전 삭제 가능한 컬럼 (쿼리에서 사용 안 됨)

#### ✅ **ri (리)**
- **사용 현황**: 쿼리에서 전혀 사용 안 됨
- **삭제 가능**: ✅ 즉시 삭제 가능

#### ✅ **bunji (번지)**
- **사용 현황**: 쿼리에서 전혀 사용 안 됨
- **삭제 가능**: ✅ 즉시 삭제 가능

#### ✅ **buildingNumber (건물 번호)**
- **사용 현황**: 쿼리에서 전혀 사용 안 됨
- **삭제 가능**: ✅ 즉시 삭제 가능

### 2. Repository 수정 후 삭제 가능한 컬럼

#### 🟡 **category (카테고리)**
- **현재 사용**:
  - `findByCategoryOrderByRatingDesc(String category)`
  - `findTop10ByCategoryOrderByRatingDesc(String category)`
- **대체 방안**: category3, category2, category1 순서로 검색
- **삭제 가능**: ✅ Repository 수정 후 삭제 가능

#### 🟡 **detailAddress (상세 주소)**
- **현재 사용**:
  - `existsByNameAndDetailAddress(String name, String detailAddress)`
  - `findByAddressAndDetailAddress(String address, String detailAddress)`
- **대체 방안**: address 필드만 사용
- **삭제 가능**: ✅ Repository 수정 후 삭제 가능

### 3. 유지해야 하는 컬럼

#### ❌ **description (설명)**
- **사용 현황**: `findByNameContaining`에서 검색용으로 사용
- **삭제 불가**: 검색 기능에 필요

## 삭제 계획

### Phase 1: 즉시 삭제 가능한 컬럼
1. `ri` (리)
2. `bunji` (번지)
3. `buildingNumber` (건물 번호)

### Phase 2: Repository 수정 후 삭제
1. `category` → category3, category2, category1로 대체
2. `detailAddress` → address로 대체

## 마이그레이션 순서

1. **Repository 메서드 수정**
   - `findByCategoryOrderByRatingDesc` → category3 기반으로 변경
   - `findTop10ByCategoryOrderByRatingDesc` → category3 기반으로 변경
   - `existsByNameAndDetailAddress` → `existsByNameAndAddress`로 변경
   - `findByAddressAndDetailAddress` → `findByAddress`로 변경

2. **Service 로직 수정**
   - category 필드 사용 부분을 category3로 변경

3. **Entity에서 컬럼 제거**
   - @Column 어노테이션 제거
   - 필드 제거

4. **Converter 수정**
   - 해당 필드 매핑 제거

5. **DTO 수정**
   - 해당 필드 제거 (선택적)

6. **DB 마이그레이션**
   - ALTER TABLE로 컬럼 삭제

