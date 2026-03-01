# Fetch 최적화 전략 (규칙)

## 개요

도메인별로 **단건 상세 조회**와 **페이징 목록 조회**에 일관된 Fetch 전략을 적용합니다.

---

## 규칙

| 조회 유형 | 전략 | 적용 방법 |
|-----------|------|-----------|
| **단건 상세 조회** | Fetch Join | `findByIdWithXXX()` 메서드에 `JOIN FETCH`로 연관 엔티티 한 번에 조회 |
| **페이징 목록 조회** | Batch Size | OneToMany 컬렉션에 `@BatchSize(size=50)` 적용, 접근 시점에 배치 조회 |

---

## 이유

### 단건 상세 → Fetch Join
- 1건이라 Fetch Join으로 한 번에 로드해도 부담 적음
- 연관 엔티티(Pet, SocialUser 등)를 별도 쿼리 없이 한 번에 로드 가능

### 페이징 목록 → Batch Size
- `Page` + OneToMany Fetch Join은 Hibernate 제약이 있음 (row 중복, count 쿼리 이슈)
- `@BatchSize`로 N+1만 줄이는 방식이 더 안전함
- 100명 조회 시: 1 + ceil(100/50) = 3 쿼리로 수렴

---

## 진행 방식

- 도메인 하나씩 지정해서 적용
- 각 도메인마다 **이유·상황·근거**를 문서로 정리
- 작업 후 **시퀀스 다이어그램**으로 흐름 검증

---

## 도메인별 문서

- [user/](./user/) - User 도메인
- [board/](./board/) - Board 도메인 (일반 게시글, Comment, 실종 제보)
- [care/](./care/) - Care 도메인 (펫케어 요청, 지원, 댓글, 리뷰)
- [location/](./location/) - Location 도메인 (장소 서비스, 장소 리뷰)
- [meetup/](./meetup/) - Meetup 도메인 (모임, 모임 참여자)
- [payment/](./payment/) - Payment 도메인 (펫코인 거래, 에스크로)
