"""
place_tags_sample.json → UPDATE SQL 생성
Usage: python scripts/generate_tag_sql.py
"""
import json
from pathlib import Path

data_path = Path(__file__).parent.parent / "data" / "output" / "place_tags_sample.json"
with open(data_path, encoding="utf-8") as f:
    records = json.load(f)

sql_lines = []
for r in records:
    tags_json = json.dumps(r["tags"], ensure_ascii=False)
    tags_json_escaped = tags_json.replace("'", "\\'")
    sql_lines.append(
        f"UPDATE locationservice SET tags = '{tags_json_escaped}' WHERE idx = {r['idx']};"
    )

out_path = Path(__file__).parent.parent / "data" / "output" / "place_tags_update.sql"
out_path.write_text("\n".join(sql_lines), encoding="utf-8")
print(f"SQL 생성 완료: {out_path} ({len(sql_lines)}건)")
