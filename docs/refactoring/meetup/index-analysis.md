# 인덱스 분석 및 쿼리 최적화 결과

## 📋 현재 인덱스 현황

| 인덱스명 | 컬럼 | 타입 | 설명 |
|---------|------|------|------|
| `idx_meetup_location` | `(latitude, longitude)` | COMPOSITE INDEX | 위치 조회용 |
| `idx_meetup_date_status` | `(date, status)` | COMPOSITE INDEX | 날짜+상태 필터링용 |
| `idx_meetup_date` | `date` | INDEX | 날짜 필터링용 |
| `idx_meetup_status` | `status` | INDEX | 상태 필터링용 |

---

## ✅ 최종 적용된 쿼리 (Bounding Box 방식)

```sql
SELECT m.* FROM meetup m 
WHERE m.date > :currentDate
  AND (m.status IS NULL OR m.status != 'COMPLETED')
  AND (m.is_deleted = false OR m.is_deleted IS NULL)
  AND m.latitude BETWEEN (:lat - :radius / 111.0) AND (:lat + :radius / 111.0)
  AND m.longitude BETWEEN (:lng - :radius / (111.0 * cos(radians(:lat)))) 
                      AND (:lng + :radius / (111.0 * cos(radians(:lat))))
  AND (6371 * acos(...)) <= :radius
ORDER BY (6371 * acos(...)) ASC, m.date ASC
```

**Bounding Box 계산식**:
- 위도 1도 ≈ 111km
- 경도 1도 ≈ 111km × cos(위도)
- 반경 5km → 위도 ±0.045도, 경도 ±0.045/cos(위도)도

---

## 📊 EXPLAIN 실행 계획 결과

### Before (인덱스 미사용)
```
type: ALL
key: NULL
rows: 2958
filtered: 4.86%
Extra: Using where; Using filesort
```

### After (Bounding Box - 인덱스 사용 성공) ✅
```
type: range
key: idx_meetup_location
key_len: 18
rows: 117
filtered: 0.60%
Extra: Using index condition; Using where; Using filesort
```

### 개선 효과

| 항목 | Before | After | 개선율 |
|------|--------|-------|--------|
| **type** | ALL | range | ✅ 인덱스 사용 |
| **key** | NULL | idx_meetup_location | ✅ 인덱스 활용 |
| **rows** | 2958 개 | 117 개 | **96.0% 감소** |

---

## 💡 최적화 과정

1. ❌ 조건 순서 재배치 → 인덱스 미사용
2. ❌ 서브쿼리 방식 → 인덱스 미사용
3. ✅ **Bounding Box 방식** → `idx_meetup_location` 인덱스 사용 성공

**성공 이유**: `BETWEEN` 조건으로 인덱스 활용 가능 (`IS NOT NULL` 조건 제거)

---

## 📝 참고

- EXPLAIN 쿼리: `explain-queries.sql` 참조
- 성능 비교: [performance-comparison.md](./performance-comparison.md)
