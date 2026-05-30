"""
locationservice DB에서 category 읽어 의도 태그 생성 → JSON 출력
Usage: python scripts/place_tag_batch.py --password <pw> --limit 500
"""
import argparse
import json
import sys
from pathlib import Path
from collections import Counter

try:
    import pymysql
except ImportError:
    print("pymysql 없음. pip install pymysql")
    sys.exit(1)

CATEGORY_TAG_MAP = {
    "동물병원":    ["medical", "veterinary", "health"],
    "동물약국":    ["medical", "pharmacy", "health"],
    "미용":        ["grooming", "bath", "fur", "nail"],
    "반려동물용품": ["supplies", "toy", "food", "accessories"],
    "여행지":      ["travel", "outdoor", "walk", "nature"],
    "반려동반여행": ["travel", "outdoor", "nature", "outing"],
    "카페":        ["cafe", "pet_friendly", "dining"],
    "식당":        ["restaurant", "dining", "pet_friendly"],
    "호텔":        ["lodging", "hotel", "overnight"],
    "펜션":        ["lodging", "pension", "overnight", "travel"],
    "위탁관리":    ["boarding", "daycare", "care", "sitter"],
    "반려문화시설": ["culture", "exhibition", "event"],
    "미술관":      ["museum", "gallery", "culture"],
    "박물관":      ["museum", "culture", "exhibition"],
    "문예회관":    ["culture", "performance", "event"],
}

def get_tags(category1, category2, category3):
    tags = set()
    for cat in [category1, category2, category3]:
        if cat and cat in CATEGORY_TAG_MAP:
            tags.update(CATEGORY_TAG_MAP[cat])
    return sorted(tags) if tags else ["general"]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host",     default="localhost")
    parser.add_argument("--user",     default="root")
    parser.add_argument("--password", default="")
    parser.add_argument("--db",       default="petory")
    parser.add_argument("--limit",    type=int, default=500)
    args = parser.parse_args()

    conn = pymysql.connect(
        host=args.host, user=args.user, password=args.password,
        database=args.db, charset="utf8mb4"
    )
    cursor = conn.cursor()
    cursor.execute(
        "SELECT idx, name, category1, category2, category3 FROM locationservice LIMIT %s",
        (args.limit,)
    )
    rows = cursor.fetchall()
    conn.close()

    results = []
    for idx, name, cat1, cat2, cat3 in rows:
        tags = get_tags(cat1, cat2, cat3)
        results.append({
            "idx": idx,
            "name": name,
            "category1": cat1,
            "category2": cat2,
            "category3": cat3,
            "tags": tags
        })

    out_path = Path(__file__).parent.parent / "data" / "output" / "place_tags_sample.json"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    print(f"저장 완료: {out_path} ({len(results)}건)")
    all_tags = [t for r in results for t in r["tags"]]
    print("태그 분포 상위 10:", Counter(all_tags).most_common(10))

if __name__ == "__main__":
    main()
