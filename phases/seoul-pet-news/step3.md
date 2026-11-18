# Step 3: Python 크롤러 — `petory-nlp-server` 에 `/crawl/seoul-pet-news`

## 목표
서울시 보도자료에서 「반려동물」 검색 결과를 긁어 **JSON 으로 반환하는 엔드포인트 하나**를 만든다.
**DB 에 쓰지 않는다.** 저장·멱등·알림은 Step 4 의 Java 가 한다 (writer 를 하나로 유지).

## 배경 / 결정
- 호출 방향은 **Java → Python** 이다. 기존 intent 분류 호출과 같은 방향이라 새로 만들 인증·스케줄러가 없다.
- `petory-nlp-server` 에는 이미 `httpx` 가 있다. HTML 파서만 추가한다.
- Step 1 에서 확정된 사실:
  - 목록: `https://www.seoul.go.kr/news/news_report.do?bbsNo=158&srchText=반려동물&curPage=N` (GET, 10건/페이지, 22페이지)
  - 상세 URL 은 목록에 없다 → `fnTbbsView('470639')` 에서 **번호만 뽑아** `https://www.seoul.go.kr/news/news_report.do#view/470639` 로 조립
  - 제목에 HTML 엔티티(`&#40;` `&apos;`)가 섞여 온다 → `html.unescape` 필요
  - robots.txt 는 `Allow: /news` 다. **Crawl-delay 는 없지만 요청 사이에 1초를 둔다.**

## 변경 파일

### 1. `petory-nlp-server/requirements.txt` (수정)
```
beautifulsoup4==4.12.3
```
`httpx` 는 이미 있다. **Playwright 는 넣지 않는다** — 목록이 정적 HTML 이라 필요 없다 (Step 1 확인).

### 2. `petory-nlp-server/app/crawler/seoul_pet_news.py` (신규)

```python
BASE = "https://www.seoul.go.kr/news/news_report.do"
KEYWORD = "반려동물"
DETAIL = "https://www.seoul.go.kr/news/news_report.do#view/{no}"
UA = "PetoryBot/1.0 (+personal project; contact via site)"   # 정체를 숨기지 않는다
DELAY_SEC = 1.0
```

- `fetch_page(page: int) -> list[dict]`
  - `httpx.get(BASE, params={"bbsNo":158, "srchText":KEYWORD, "curPage":page}, headers={"User-Agent":UA}, timeout=10)`
  - BeautifulSoup 으로 목록 행 파싱 → `source_no`(fnTbbsView 인자), `title`(unescape), `dept`, `published_at`(YYYY-MM-DD)
  - 행에서 번호를 못 뽑으면 그 행만 버리고 `skipped_invalid` 로 센다 (전체를 죽이지 않는다)
- `classify(title: str) -> str | None` — **룰 분류** (D3 결정)
  - `모집|참가|신청|접수|행사|축제|페스타|대회` → `EVENT`
  - `교육|강좌|아카데미|세미나|설명회` → `EDUCATION`
  - `주의|수칙|예방|경보|안전|감시` → `SAFETY`
  - `지원|보조|무료|지원사업` → `SUPPORT`
  - 아무것도 안 걸리면 **`None` 을 반환해 버린다** (보도참고자료·시의원 질의 같은 잡음 제거)
  - `# ponytail: 키워드 룰. 분기가 20줄을 넘거나 오분류가 눈에 띄면 app/nlp 의 intent classifier 에 라벨을 붙여 옮긴다`
- `crawl(max_pages: int = 1) -> dict` — 페이지를 돌며 모아서 반환. 페이지 사이 `time.sleep(DELAY_SEC)`.

### 3. `petory-nlp-server/app/api/crawl_router.py` (신규)
- `GET /crawl/seoul-pet-news?max_pages=1`
- 응답:
```json
{ "fetched": 10, "skipped_invalid": 0, "skipped_unclassified": 4,
  "items": [ {"sourceNo":"468820","title":"...","dept":"동물보호과",
              "publishedAt":"2026-10-26","category":"EVENT",
              "sourceUrl":"https://www.seoul.go.kr/news/news_report.do#view/468820"} ] }
```
- **예외를 밖으로 던지지 않는다.** 네트워크·파싱 실패 시 `items: []` 와 `error` 문자열을 담아 200 으로 돌려준다 — Java 스케줄러가 이걸로 `LocationSyncLog.failed` 를 기록한다.

### 4. `petory-nlp-server/app/main.py` (수정)
- `app.include_router(crawl_router)` 한 줄만 추가. **lifespan·임베딩 로딩은 건드리지 않는다.**

## 하지 않을 것
- 상세 페이지 요청 (마감일·본문) — 행마다 1회가 되어 요청량이 행 수에 비례한다. Step 2 와 같은 이유로 제외.
- 본문 저장 (저작권)
- Playwright

## AC (Acceptance Criteria)

```bash
cd /Users/maknkkong/project/Petory/petory-nlp-server
source venv/bin/activate && pip install -r requirements.txt
./run.sh &

# 1) 1페이지 수집 — items 가 1건 이상이고 각 항목에 sourceNo/title/category/sourceUrl 이 있어야 한다
curl -s 'http://localhost:8000/crawl/seoul-pet-news?max_pages=1' | python3 -m json.tool | head -40

# 2) 분류가 실제로 거른다 — skipped_unclassified 가 0 이 아니어야 정상 (보도참고자료 잡음이 섞여 들어온다)
# 3) 두 번 호출해도 같은 sourceNo 집합이 나온다 (크롤러는 상태가 없다)
# 4) 네트워크를 끊고 호출 → 500 이 아니라 items: [] + error 문자열로 200 이 와야 한다
```
