"""서울시 보도자료에서 「반려동물」 소식을 긁는다.

2026-11-18 조사로 확정된 사실만 쓴다.

- robots.txt 는 `Disallow: /` + `Allow: /news` 다. 이 경로는 허용 쪽이고 Crawl-delay 는 없지만
  페이지 사이에 1초를 둔다.
- 목록은 정적 HTML 이라 Playwright 가 필요 없다. 표 구조는
  `table.sib-lst-type-basic > tbody > tr` 에 번호·제목·담당부서·등록일 4칸이다.
- 검색 파라미터는 `srchText` 만 먹는다. `searchWrd` 는 무시되고 전체 64,415건이 그대로 나온다.
- 상세 링크는 목록에 없고 `javascript:fnTbbsView('468820')` 뿐이다. 그 함수가
  `location.href = "?bbsNo=...&nttNo=..."` 로 가므로 **번호만 뽑아 같은 형식으로 조립한다**
  (RSS 의 `#view/<번호>` 해시보다 낫다 — 해시는 서버로 가지 않는다).
- 본문은 가져오지 않는다. 원문에 공공누리 표시가 확인되지 않아 사실 정보만 쓴다.
"""

from __future__ import annotations

import html
import logging
import re
import time

import requests
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

BASE_URL = "https://www.seoul.go.kr/news/news_report.do"
BBS_NO = "158"
KEYWORD = "반려동물"
SOURCE = "SEOUL_PRESS"

# 정체를 숨기지 않는다. 검색엔진 봇을 사칭하지 않는다.
USER_AGENT = "PetoryBot/1.0 (+personal project; pet care service)"
DELAY_SEC = 1.0
TIMEOUT_SEC = 10.0
MAX_PAGES_CAP = 25  # 「반려동물」 검색이 22페이지다. 무한 루프 방지선.

_ROW_SELECTOR = "table.sib-lst-type-basic tbody tr"
_NTT_NO = re.compile(r"fnTbbsView\('(\d+)'\)")
_YMD = re.compile(r"^\d{4}-\d{2}-\d{2}$")

# 1차 관문: 제목에 반려동물 신호어가 있어야 한다.
#
# 🔴 검색(srchText)이 제목뿐 아니라 「본문까지」 훑는다. 그래서 「세텍 전시장 정기 대관 모집」,
#    「고립은둔청년은 물론 가족도 돌본다」처럼 반려동물과 무관한 보도자료가 결과에 섞여 들어온다
#    (2026-11-18 실측, 1~2페이지 20건 중 5건). 이 관문이 없으면 그런 기사가 알림으로 나간다.
#    대신 제목에 단어가 없고 본문에만 있는 기사(예: 「도심공원 진드기 집중 감시」)는 놓친다.
#    알림으로 나가는 자리라 「놓치는 쪽」을 택한다.
_PET_WORDS = ("반려", "댕댕", "댕냥", "강아지", "고양이", "동물", "펫", "유기견", "입양")

# 2차: 분류. 제목 키워드 룰이다.
# ponytail: 키워드 룰. 분기가 20줄을 넘거나 오분류가 눈에 띄면
#           app/nlp 의 intent classifier 에 라벨을 붙여 옮긴다.
_RULES = [
    ("EDUCATION", ("교육", "강좌", "아카데미", "세미나", "설명회", "교실")),
    ("SUPPORT", ("지원사업", "지원", "보조금", "무료", "돌봄")),
    ("SAFETY", ("주의", "수칙", "예방", "경보", "안전", "감시", "사칭")),
    ("EVENT", ("모집", "참가", "신청", "접수", "행사", "축제", "페스타", "대회", "개최",
               "입양", "체험", "위크", "프로그램")),
]

# 관문은 넘었는데 분류가 안 되는 소식도 소식이다 — 「서울동물행복위크 시작」,
# 「반려동물을 보낸 슬픔, 서울시와 함께 치유하세요」가 그렇다. 버리지 않고 NEWS 로 둔다.
_FALLBACK = "NEWS"


def classify(title: str) -> str | None:
    """제목으로 분류한다. 반려동물 신호어가 없으면 None — 적재하지 않는다."""
    if not any(word in title for word in _PET_WORDS):
        return None
    for category, words in _RULES:
        if any(word in title for word in words):
            return category
    return _FALLBACK


def _parse_row(tr) -> dict | None:
    """행 하나를 항목으로. 쓸 수 없으면 None (그 행만 버리고 전체는 살린다)."""
    link = tr.select_one("td.sib-lst-type-basic-subject a")
    if link is None:
        return None

    matched = _NTT_NO.search(link.get("href", ""))
    if matched is None:
        return None
    ntt_no = matched.group(1)

    cells = tr.find_all("td")
    if len(cells) < 4:
        return None

    published = cells[-1].get_text(strip=True)
    if not _YMD.match(published):
        return None

    title = html.unescape(link.get_text(strip=True))
    if not title:
        return None

    return {
        "sourceNo": ntt_no,
        "title": title,
        "dept": cells[2].get_text(strip=True) or None,
        "publishedAt": published,
        "sourceUrl": f"{BASE_URL}?bbsNo={BBS_NO}&nttNo={ntt_no}",
    }


def _fetch_page(session: requests.Session, page: int) -> list:
    response = session.get(
        BASE_URL,
        params={"bbsNo": BBS_NO, "srchText": KEYWORD, "curPage": page},
        timeout=TIMEOUT_SEC,
    )
    response.raise_for_status()
    soup = BeautifulSoup(response.text, "html.parser")
    return soup.select(_ROW_SELECTOR)


def crawl(max_pages: int = 1) -> dict:
    """목록을 긁어 분류까지 끝낸 항목을 돌려준다.

    예외를 밖으로 던지지 않는다. 실패해도 200 으로 ``error`` 를 담아 돌려주고,
    Spring 쪽이 그것으로 동기화 이력(status=FAILED)을 남긴다.
    """
    pages = max(1, min(max_pages, MAX_PAGES_CAP))
    items, fetched, skipped_invalid, skipped_unclassified = [], 0, 0, 0
    error = None

    try:
        with requests.Session() as session:
            session.headers.update({"User-Agent": USER_AGENT})
            for page in range(1, pages + 1):
                if page > 1:
                    time.sleep(DELAY_SEC)

                rows = _fetch_page(session, page)
                if not rows:
                    break  # 마지막 페이지를 넘어섰다

                for row in rows:
                    fetched += 1
                    item = _parse_row(row)
                    if item is None:
                        skipped_invalid += 1
                        continue
                    category = classify(item["title"])
                    if category is None:
                        skipped_unclassified += 1
                        continue
                    items.append({**item, "source": SOURCE, "category": category})
    except Exception as exc:  # 네트워크·파싱·구조 변경 전부
        error = f"{type(exc).__name__}: {exc}"
        logger.error("서울시 보도자료 수집 실패 page=%s error=%s", pages, error, exc_info=True)

    return {
        "source": SOURCE,
        "fetched": fetched,
        "skippedInvalid": skipped_invalid,
        "skippedUnclassified": skipped_unclassified,
        "items": items,
        "error": error,
    }


__all__ = ["crawl", "classify", "SOURCE"]
