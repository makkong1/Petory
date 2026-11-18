import logging

from fastapi import APIRouter, Query

from app.crawler.seoul_pet_news import crawl

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/crawl", tags=["crawl"])


@router.get("/seoul-pet-news")
def seoul_pet_news(max_pages: int = Query(1, ge=1, le=25)):
    """서울시 보도자료 「반려동물」 소식을 긁어 돌려준다. 저장은 Spring 이 한다.

    실패해도 500 을 내지 않는다 — ``items: []`` 와 ``error`` 를 담아 200 으로 돌려주고
    호출한 쪽이 동기화 이력에 남긴다.
    """
    result = crawl(max_pages=max_pages)
    logger.info(
        "서울시 보도자료 수집: pages=%d fetched=%d items=%d invalid=%d unclassified=%d error=%s",
        max_pages, result["fetched"], len(result["items"]),
        result["skippedInvalid"], result["skippedUnclassified"], result["error"],
    )
    return result
