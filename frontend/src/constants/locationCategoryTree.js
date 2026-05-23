/**
 * 공공·내부 원천 데이터의 위치 서비스 3단 계층 (대·중·소) 기준 트리.
 * 백엔드 GET /api/location-services/search 의 `category` 는 이 중 하나의 문자열과
 * DB 의 category1|category2|category3 과 일치하면 필터된다.
 *
 * 대분류(반려동물업)는 보통 검색폭이 넓어 UI 에서는 제외하고, 중·소만 선택 가능하게 했다.
 */
export const PET_PLACE_CATEGORY_GROUPS = [
  {
    id: "medical",
    label: "반려의료",
    apiValue: "반려의료",
    leaves: [
      { apiValue: "동물병원", label: "동물병원" },
      { apiValue: "동물약국", label: "동물약국" },
    ],
  },
  {
    id: "travel",
    label: "반려동반여행",
    apiValue: "반려동반여행",
    leaves: [
      { apiValue: "미술관", label: "미술관" },
      { apiValue: "문예회관", label: "문예회관" },
      { apiValue: "펜션", label: "펜션" },
      { apiValue: "여행지", label: "여행지" },
      { apiValue: "박물관", label: "박물관" },
      { apiValue: "호텔", label: "호텔" },
    ],
  },
  {
    id: "dining",
    label: "식당·카페",
    apiValue: "반려동물식당카페",
    leaves: [
      { apiValue: "카페", label: "카페" },
      { apiValue: "식당", label: "식당" },
    ],
  },
  {
    id: "service",
    label: "용품·서비스",
    apiValue: "반려동물 서비스",
    leaves: [
      { apiValue: "반려동물용품", label: "용품·펫샵" },
      { apiValue: "미용", label: "미용" },
      { apiValue: "위탁관리", label: "위탁관리" },
    ],
  },
  {
    id: "culture",
    label: "반려문화시설",
    apiValue: "반려문화시설",
    leaves: [{ apiValue: "미술관", label: "미술관" }],
  },
];

/**
 * 같은 소분류(apiValue)가 여러 중분류에 있을 때(예: 카페, 미술관),
 * category 문자열만 있고 UI 가 어느 중분류인지 모를 때의 기본 축.
 */
export const DUPLICATE_LEAF_DEFAULT_GROUP_ID = {
  카페: "dining",
  미술관: "culture",
};

/**
 * UI 에서 열릴 중분류 블록 결정.
 * @param {string} [category] API 로 보내는 category (중·소 문자열)
 * @param {string|null} [activeGroupId] 사용자가 마지막으로 고른 그룹 id (우선)
 */
export function resolveActiveGroup(category, activeGroupId) {
  if (activeGroupId) {
    const byId = PET_PLACE_CATEGORY_GROUPS.find((g) => g.id === activeGroupId);
    if (byId) return byId;
  }
  if (!category) return null;

  const matches = PET_PLACE_CATEGORY_GROUPS.filter(
    (g) =>
      g.apiValue === category || g.leaves.some((l) => l.apiValue === category),
  );
  if (matches.length === 0) return null;
  if (matches.length === 1) return matches[0];

  const prefId = DUPLICATE_LEAF_DEFAULT_GROUP_ID[category];
  if (prefId) {
    const pref = matches.find((g) => g.id === prefId);
    if (pref) return pref;
  }
  return matches[0];
}
