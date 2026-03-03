# 관리자 페이지 UI 통합 분석

## 1. 현재 구조 비교

### 1.1 공통 패턴 (Report, Community, Care, MissingPet, Meetup)

| 요소 | 구조 |
|------|------|
| **Wrapper** | `Wrapper` (빈 div) |
| **Header** | `Title` + `Subtitle` |
| **Filters** | `Filters` > `Group`(Label + Select/Input) + `Refresh` 버튼 |
| **Card** | `Card` (border, padding, surfaceSoft) |
| **Content** | `Table` (thead/tbody) 또는 `Info` (로딩/에러/빈 목록) |
| **Pagination** | `PageNavigation` (일부만) |
| **버튼** | `ViewButton` 등 테마 기반 |

### 1.2 사용자 관리 (UserManagementSection + UserList)

| 요소 | 구조 | 차이 |
|------|------|------|
| **Wrapper** | UserManagementSection: `Wrapper` > `Header` + `Content` | 이중 래퍼 |
| **Header** | UserManagementSection: Title + Subtitle<br>UserList: **별도** "👥 사용자 관리" + PageSizeSelect + AddButton | **헤더 중복** |
| **Filters** | **없음** | 상태/삭제/검색 필터 없음 |
| **Card** | **없음** | Card wrapper 미사용 |
| **Content** | **UserGrid + UserCard** (카드 그리드) | **테이블 아님** |
| **Pagination** | `PageNavigation` | 동일 |
| **버튼** | `ActionButton` (variant: edit/delete/restore) | 하드코딩 색상 (#3498db, #e74c3c) |
| **Container** | UserList: `Container` (padding, max-width: 1400px, margin: auto) | 다른 섹션은 AdminLayout Content 내부 |

---

## 2. 차이점 요약

| 항목 | 다른 관리 페이지 | 사용자 관리 |
|------|-----------------|------------|
| **목록 레이아웃** | 테이블 (Table) | 카드 그리드 (UserCard) |
| **헤더** | 1개 (Title + Subtitle) | 2개 (섹션 + UserList 각각) |
| **필터** | Filters bar (상태, 삭제, 검색 등) | 없음 |
| **Card 래퍼** | 있음 | 없음 |
| **버튼 스타일** | 테마 기반 | 하드코딩 색상 |
| **에러/로딩** | Card 내부 Info | 별도 LoadingMessage, ErrorMessage |
| **페이지 크기** | Filters 내 Select | HeaderRight에 별도 배치 |

---

## 3. 통합 방안

### 3.1 방안 A: UserList를 테이블 레이아웃으로 통일 (권장)

**목표**: 사용자 관리도 다른 관리 페이지와 동일한 패턴 적용

| 변경 | 내용 |
|------|------|
| UserManagementSection | UserList 대신 **AdminUserList** 또는 UserList에 `variant="admin"` 전달 |
| 레이아웃 | UserCard 그리드 → **Table** (thead: ID, 이름, 이메일, 역할, 상태, 삭제여부, 액션) |
| 헤더 | UserList 내부 "👥 사용자 관리" 제거, UserManagementSection Header만 사용 |
| 필터 | Filters bar 추가 (역할, 삭제여부, 검색) |
| Card | Card wrapper로 테이블 감싸기 |
| 버튼 | ActionButton → 테마 기반 ViewButton/AdminButton 스타일 |

**장점**: 전체 관리자 UI 일관성 확보  
**단점**: UserList 기존 카드 레이아웃 변경 (다른 곳에서 사용 시 분기 필요)

---

### 3.2 방안 B: UserList는 유지, UserManagementSection 래퍼만 정리

**목표**: 최소 변경으로 헤더 중복·스타일만 맞추기

| 변경 | 내용 |
|------|------|
| UserManagementSection | UserList에 `showHeader={false}` 전달 (또는 UserList가 admin context 감지) |
| UserList | `showHeader` prop으로 내부 "👥 사용자 관리" 헤더 숨김 |
| UserList | Container에 Card 스타일 적용 (border, surfaceSoft) |
| UserList | ActionButton에 하드코딩 색상 제거, theme 사용 |

**장점**: 변경 범위 작음  
**단점**: 카드 vs 테이블 레이아웃 차이는 그대로

---

### 3.3 방안 C: 공통 AdminSection 컴포넌트 추출

**목표**: 공통 레이아웃 컴포넌트로 재사용

```
AdminSection/
  ├── AdminSectionHeader (Title, Subtitle)
  ├── AdminFilters (Group, Label, Select, Input, Refresh)
  ├── AdminCard (Table wrapper)
  ├── AdminTable (기본 테이블 스타일)
  └── AdminPageNavigation
```

각 관리 섹션에 `AdminSection` 적용, UserList는 `AdminSection` + `AdminTable`로 감싸서 테이블 형태로 전환.

**장점**: 향후 확장·유지보수 용이  
**단점**: 리팩토링 범위 큼

---

## 4. 권장 작업 순서

1. **1단계 (즉시)**: UserManagementSection에서 UserList 내부 헤더 중복 제거
   - UserList에 `embeddedInAdmin` prop 추가 → `embeddedInAdmin`이면 "👥 사용자 관리" 헤더 숨김

2. **2단계**: UserList를 Card로 감싸고, Filters bar 추가 (역할, 삭제여부, 검색)  
   - 다른 관리 페이지와 동일한 Filters 패턴 적용

3. **3단계 (선택)**: UserList를 테이블 레이아웃으로 전환  
   - Admin 전용일 때만 테이블, 일반 UserList는 카드 유지 (variant prop)

4. **4단계 (선택)**: UserList ActionButton 색상 테마화  
   - `#3498db`, `#e74c3c` → `theme.colors.primary`, `theme.colors.error` 등

---

## 5. 최종 정리

| 우선순위 | 작업 | 효과 |
|---------|------|------|
| 높음 | 헤더 중복 제거 | 시각적 혼란 감소 |
| 높음 | Card + Filters 패턴 적용 | 다른 관리 페이지와 구조 통일 |
| 중간 | 테이블 레이아웃 전환 | 관리자 UI 완전 통일 |
| 낮음 | 공통 AdminSection 컴포넌트 | 장기 유지보수성 향상 |
