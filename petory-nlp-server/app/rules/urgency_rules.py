_HIGH_URGENCY_KEYWORDS = [
    "응급", "위급", "쓰러", "피", "출혈", "숨", "호흡", "경련", "발작",
    "못 먹", "이틀", "사흘", "며칠째", "계속", "심해"
]

_LOW_URGENCY_DOMAINS = {
    "SUPPLIES", "FOOD_SNACK", "WALK_OUTING", "CAFE_DINING",
    "LODGING_TRAVEL", "CULTURE_SPACE"
}

def judge_urgency(text: str, intent_domain: str) -> str:
    if intent_domain in _LOW_URGENCY_DOMAINS:
        return "LOW"
    for kw in _HIGH_URGENCY_KEYWORDS:
        if kw in text:
            return "HIGH"
    return "NORMAL"
