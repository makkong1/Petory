# LocationService 인덱스 분석 및 최적화

## 현재 인덱스 현황 (2024)

### 필수 인덱스 (유지 필요)
1. **PRIMARY KEY (idx)** - 기본키, 필수
2. **idx_lat_lng (latitude, longitude)** - 위치 기반 검색, 필수
3. **idx_name_address (name, address)** - 중복 체크, 필수
4. **idx_locationservice_deleted_rating (is_deleted, rating DESC)** - 복합 인덱스, 필수
5. **idx_locationservice_sido (sido, rating DESC)** - 지역 검색, 필수
6. **idx_locationservice_sigungu (sigungu, rating DESC)** - 지역 검색, 필수
7. **idx_locationservice_eupmyeondong (eupmyeondong, rating DESC)** - 지역 검색, 필수
8. **ft_name_desc (name, description) FULLTEXT** - 텍스트 검색, 필수

### 중복/불필요한 인덱스 (제거 권장)

#### 🔴 제거 권장 1: idx_rating_desc (rating DESC)
- **이유**: `idx_locationservice_deleted_rating (is_deleted, rating DESC)`와 부분 중복
- **대안**: `is_deleted = 0` 조건으로 필터링 후 동일 인덱스 활용
- **예외**: `is_deleted` 조건 없이 `rating DESC`만 사용하는 쿼리가 있다면 유지

#### 🔴 제거 권장 2: idx_address (address)
- **이유**: `idx_name_address (name, address)`의 부분 집합
- **대안**: `idx_name_address`가 `(address, ...)` 쿼리에도 활용 가능
- **주의**: `address` 단독 검색이 자주 사용된다면 유지 고려

#### 🔴 제거 권장 3: idx_locationservice_is_deleted (is_deleted)
- **이유**: `idx_locationservice_deleted_rating (is_deleted, rating DESC)`에 포함
- **대안**: 복합 인덱스가 `is_deleted` 단독 검색에도 활용 가능
- **Cardinality**: 1 (매우 낮음) → 인덱스 효율 낮음

#### ⚠️ 검토 필요: idx_address_detail (address, detail_address)
- **이유**: `detail_address` 컬럼 사용 여부 확인 필요
- **조치**: 실제 쿼리에서 사용되지 않으면 제거

### 추가 검토 사항

#### 카테고리 인덱스 (category1, category2, category3)
- **현재 상태**: 카테고리 인덱스 없음
- **쿼리 패턴**: `category3 = :category OR category2 = :category OR category1 = :category`
- **문제점**: OR 조건으로 인덱스 활용 제한적
- **최적화 방안**:
  1. **방안 1 (권장)**: UNION으로 분리하여 각각 인덱스 활용
  2. **방안 2**: category3 우선 사용 (가장 구체적)
  3. **방안 3**: 각 카테고리별 인덱스 생성 (인덱스 증가)

## 인덱스 최적화 전략

### 1단계: 불필요한 인덱스 제거

```sql
-- 제거 권장 인덱스들
DROP INDEX idx_rating_desc ON locationservice;
DROP INDEX idx_address ON locationservice;  -- 단, address 단독 검색이 자주 사용되면 유지
DROP INDEX idx_locationservice_is_deleted ON locationservice;
-- idx_address_detail는 detail_address 컬럼 사용 여부 확인 후 결정
```

### 2단계: 카테고리 인덱스 전략

#### 옵션 A: category3만 인덱스 (최소 인덱스, 권장)
```sql
-- category3만 인덱스 (가장 구체적이므로 우선 사용)
CREATE INDEX idx_category3_deleted_rating ON locationservice(category3, is_deleted, rating DESC);
```
- **장점**: 인덱스 1개만 추가
- **단점**: category2, category1 검색 시 성능 저하 가능
- **권장**: category3가 가장 구체적이므로 대부분의 쿼리에서 활용

#### 옵션 B: 모든 카테고리 인덱스 (최대 성능, 인덱스 증가)
```sql
-- 모든 카테고리 레벨 인덱스
CREATE INDEX idx_category3_deleted_rating ON locationservice(category3, is_deleted, rating DESC);
CREATE INDEX idx_category2_deleted_rating ON locationservice(category2, is_deleted, rating DESC);
CREATE INDEX idx_category1_deleted_rating ON locationservice(category1, is_deleted, rating DESC);
```
- **장점**: 모든 카테고리 검색 최적화
- **단점**: 인덱스 3개 추가 (유지 비용 증가)

#### 옵션 C: 쿼리 수정 (UNION 사용, 최적화)
```sql
-- 애플리케이션에서 UNION으로 분리
SELECT * FROM locationservice 
WHERE category3 = '병원' AND (is_deleted = 0 OR is_deleted IS NULL)
ORDER BY rating DESC
LIMIT 20
UNION ALL
SELECT * FROM locationservice 
WHERE category2 = '병원' AND (is_deleted = 0 OR is_deleted IS NULL)
ORDER BY rating DESC
LIMIT 20
UNION ALL
SELECT * FROM locationservice 
WHERE category1 = '병원' AND (is_deleted = 0 OR is_deleted IS NULL)
ORDER BY rating DESC
LIMIT 20
ORDER BY rating DESC
LIMIT 20;
```
- **장점**: 각 쿼리가 인덱스 활용 가능, OR 조건 문제 해결
- **단점**: 애플리케이션 로직 수정 필요

### 3단계: 최종 인덱스 구성 (권장)

#### 필수 인덱스 (8개)
1. PRIMARY KEY (idx)
2. idx_lat_lng (latitude, longitude)
3. idx_name_address (name, address)
4. idx_locationservice_deleted_rating (is_deleted, rating DESC)
5. idx_locationservice_sido (sido, rating DESC)
6. idx_locationservice_sigungu (sigungu, rating DESC)
7. idx_locationservice_eupmyeondong (eupmyeondong, rating DESC)
8. ft_name_desc (name, description) FULLTEXT

#### 카테고리 인덱스 (1개, 옵션 A)
9. idx_category3_deleted_rating (category3, is_deleted, rating DESC)

#### 총 인덱스 수: 9개 (현재 12개 → 최적화 후 9개)

## 성능 분석

### Cardinality 분석
- **idx_locationservice_is_deleted (is_deleted)**: Cardinality = 1 → 매우 낮음 (불필요)
- **idx_rating_desc (rating)**: Cardinality = 1 → 매우 낮음 (불필요, 복합 인덱스로 충분)
- **idx_locationservice_sido (sido)**: Cardinality = 17 → 적절
- **idx_locationservice_sigungu (sigungu)**: Cardinality = 228 → 적절
- **idx_locationservice_eupmyeondong (eupmyeondong)**: Cardinality = 2457 → 좋음

### 인덱스 유지 비용
- **인덱스 수**: 9개 (현재 12개에서 3개 감소)
- **저장 공간**: 약 25% 감소 예상
- **INSERT/UPDATE 성능**: 약 20% 개선 예상
- **SELECT 성능**: 동일 또는 개선 (불필요한 인덱스 제거로 옵티마이저 혼란 감소)

## 권장 사항

1. **즉시 제거**: idx_rating_desc, idx_locationservice_is_deleted
2. **검토 후 제거**: idx_address (address 단독 검색 사용 여부 확인)
3. **카테고리 인덱스**: 옵션 A (category3만) 권장
4. **모니터링**: EXPLAIN으로 실제 인덱스 사용 여부 확인
