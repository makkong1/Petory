# Step 1: Python 배치 스크립트 — category → tags 매핑 + JSON 출력

## 목표
`locationservice` 테이블의 `category1/2/3` 값을 기반으로 의도 태그를 생성하고
`data/output/place_tags_sample.json`에 저장한다.

## 배경
- `locationservice` 테이블: id=`idx`, category1/2/3, name, address
- 현재 `PetRecommendScoreCalculator`에서 `tag_match_score = 0` (Phase 4 전까지 비활성)
- 이 스크립트가 출력한 JSON을 Step 2에서 DB에 반영하면 `tag_match_score` 활성화 가능
- 카테고리 → 의도 태그 매핑은 `app/rules/category_rules.py`의 DOMAIN_TO_CATEGORIES 역방향

## 카테고리 → 태그 매핑

```
동물병원, 동물약국  → ["medical", "veterinary", "health"]
미용              → ["grooming", "bath", "fur", "nail"]
반려동물용품       → ["supplies", "toy", "food", "accessories"]
여행지, 반려동반여행 → ["travel", "outdoor", "walk", "nature"]
카페              → ["cafe", "pet_friendly", "dining"]
식당              → ["restaurant", "dining", "pet_friendly"]
호텔, 펜션         → ["lodging", "hotel", "travel", "overnight"]
위탁관리           → ["boarding", "daycare", "care"]
반려문화시설        → ["culture", "exhibition", "museum"]
미술관             → ["museum", "gallery", "culture"]
박물관             → ["museum", "culture", "exhibition"]
문예회관           → ["culture", "performance", "event"]
```

## 생성할 파일

### `petory-nlp-server/scripts/__init__.py` (빈 파일)

### `petory-nlp-server/scripts/place_tag_batch.py`

```python
"""
locationservice DB에서 category 읽어 의도 태그 생성 → JSON 출력
Usage: python scripts/place_tag_batch.py --host localhost --user root --password <pw> --db petory --limit 500
"""
import argparse
import json
import sys
from pathlib import Path

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
    # 태그 분포 출력
    from collections import Counter
    all_tags = [t for r in results for t in r["tags"]]
    print("태그 분포 상위 10:", Counter(all_tags).most_common(10))

if __name__ == "__main__":
    main()
```

## requirements.txt에 추가

```
pymysql==1.1.1
```

## Acceptance Criteria

```bash
cd /Users/maknkkong/project/Petory/petory-nlp-server
source venv/bin/activate
pip install pymysql -q
python scripts/place_tag_batch.py --password <DB비번> --limit 500
# 기대: data/output/place_tags_sample.json 생성, 태그 분포 출력
cat data/output/place_tags_sample.json | python -c "import json,sys; d=json.load(sys.stdin); print(f'{len(d)}건, 첫번째:', d[0])"
```
