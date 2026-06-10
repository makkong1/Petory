# Step 4 — Navigation.js + UnifiedPetMapPage.js CustomEvent 연동

## 목적
알림 클릭 시 `unified-map` 탭으로 이동하고, 동물병원 카테고리가 자동 선택되게 한다.

## 라우팅 기준
프론트는 `notification.type === 'PET_HEALTH_ALERT'`를 기준으로 분기한다.
(`relatedType`이 아님 — 기존 BOARD/CARE_REQUEST는 relatedType 기준이지만 이 타입은 type 기준으로 처리)

## CustomEvent 설계
| 필드 | 값 |
|------|-----|
| 이벤트명 | `'navigateToHealthAlert'` |
| `detail.category` | `'동물병원'` |
| `detail.groupId` | `'medical'` |

`locationCategoryTree.js` 기준: group `id: "medical"`, leaf `apiValue: "동물병원"`.

---

## 수정 파일 1 — Navigation.js

**경로**: `frontend/src/components/Layout/Navigation.js`

### 변경: handleNotificationClick() 에 PET_HEALTH_ALERT 케이스 추가

현재 코드 (`Navigation.js:215` 부근):
```js
} else if (notification.relatedType === 'CARE_REQUEST' && notification.relatedId) {
  setActiveTab('unified-map');
}
```

변경 후 — 아래 else if 블록을 CARE_REQUEST 케이스 다음에 추가:
```js
} else if (notification.relatedType === 'CARE_REQUEST' && notification.relatedId) {
  setActiveTab('unified-map');
} else if (notification.type === 'PET_HEALTH_ALERT') {
  setActiveTab('unified-map');
  setTimeout(() => {
    window.dispatchEvent(new CustomEvent('navigateToHealthAlert', {
      detail: { category: '동물병원', groupId: 'medical' }
    }));
  }, 100);
}
```

setTimeout 100ms는 탭 전환 후 UnifiedPetMapPage가 마운트될 시간을 준다 (기존 openBoardDetail 패턴과 동일).

---

## 수정 파일 2 — UnifiedPetMapPage.js

**경로**: `frontend/src/components/UnifiedMap/UnifiedPetMapPage.js`

### 변경: navigateToHealthAlert CustomEvent 리스너 추가

기존 `useEffect` 블록들이 있는 위치(약 line 100 이후) 중 적절한 자리에 추가:

```js
useEffect(() => {
  const handler = (e) => {
    const { category, groupId } = e.detail || {};
    setActiveLayer('location');
    setLocationCategory(category || '동물병원');
    setLocationCategoryGroupId(groupId || 'medical');
  };
  window.addEventListener('navigateToHealthAlert', handler);
  return () => window.removeEventListener('navigateToHealthAlert', handler);
}, []);
```

`setActiveLayer`, `setLocationCategory`, `setLocationCategoryGroupId`는 모두 이미 컴포넌트 내부에 `useState`로 선언되어 있다. 별도 import 불필요.

---

## AC (Acceptance Criteria)
수동 검증 (브라우저):
1. 백엔드에서 MEDICAL+HIGH signal이 저장되면 SSE 알림이 도착한다.
2. 알림 벨 아이콘 클릭 → "반려동물 건강 알림" 항목 확인.
3. 해당 알림 클릭 → `unified-map` 탭으로 이동 + 동물병원 카테고리 자동 선택.

빌드 확인:
```bash
# 프론트엔드 (frontend/ 디렉토리에서)
npm run build
```
빌드 오류 없이 통과해야 한다.
