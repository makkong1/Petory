from typing import List

DOMAIN_TO_CATEGORIES: dict[str, List[str]] = {
    "MEDICAL":          ["동물병원", "동물약국"],
    "GROOMING":         ["미용"],
    "SUPPLIES":         ["반려동물용품"],
    "FOOD_SNACK":       ["반려동물용품"],
    "WALK_OUTING":      ["여행지", "반려동반여행"],
    "CAFE_DINING":      ["카페", "식당"],
    "LODGING_TRAVEL":   ["호텔", "펜션"],
    "DAYCARE_BOARDING": ["위탁관리"],
    "CULTURE_SPACE":    ["반려문화시설", "미술관", "박물관", "문예회관"],
}

def get_categories(intent_domain: str) -> List[str]:
    return DOMAIN_TO_CATEGORIES.get(intent_domain, [])

def get_suggested_categories(top_n: int = 3) -> List[str]:
    return ["동물병원", "미용", "반려동물용품"][:top_n]
