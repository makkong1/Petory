# Step 2: DB 마이그레이션 — locationservice에 tags 컬럼 추가 + SQL import

## 목표
`locationservice` 테이블에 `tags JSON` 컬럼을 추가하고,
Step 1에서 생성한 JSON 파일을 기반으로 UPDATE SQL을 실행한다.

## 배경
- Step 1 산출물: `petory-nlp-server/data/output/place_tags_sample.json`
- `locationservice` 테이블 PK: `idx` (BIGINT)
- MySQL 8.0 JSON 타입 사용 가능
- Spring `LocationService` 엔티티는 Step 3에서 업데이트

## 실행할 DDL

```sql
ALTER TABLE locationservice
  ADD COLUMN tags JSON NULL COMMENT '반려생활 의도 태그 목록';
```

## Python으로 UPDATE SQL 생성

```python
# petory-nlp-server/scripts/generate_tag_sql.py
import json
from pathlib import Path

data_path = Path("data/output/place_tags_sample.json")
with open(data_path, encoding="utf-8") as f:
    records = json.load(f)

sql_lines = []
for r in records:
    tags_json = json.dumps(r["tags"], ensure_ascii=False)
    tags_json_escaped = tags_json.replace("'", "\\'")
    sql_lines.append(
        f"UPDATE locationservice SET tags = '{tags_json_escaped}' WHERE idx = {r['idx']};"
    )

out_path = Path("data/output/place_tags_update.sql")
out_path.write_text("\n".join(sql_lines), encoding="utf-8")
print(f"SQL 생성 완료: {out_path} ({len(sql_lines)}건)")
```

## Acceptance Criteria

```bash
# 1. DDL 실행 (MySQL 실행 중인 상태)
mysql -u root -p petory -e "ALTER TABLE locationservice ADD COLUMN IF NOT EXISTS tags JSON NULL;"

# 2. UPDATE SQL 생성
cd /Users/maknkkong/project/Petory/petory-nlp-server
source venv/bin/activate
python scripts/generate_tag_sql.py

# 3. SQL 적용
mysql -u root -p petory < data/output/place_tags_update.sql

# 4. 검증
mysql -u root -p petory -e "SELECT idx, name, tags FROM locationservice WHERE tags IS NOT NULL LIMIT 3;"
# 기대: tags 컬럼에 JSON 배열 값 확인
```

> MySQL 접속 비밀번호는 로컬 환경에 맞게 입력.
> `ADD COLUMN IF NOT EXISTS`는 MySQL 8.0.3+ 지원. 이전 버전이면 `ADD COLUMN`만 사용 후 에러 시 스킵.
