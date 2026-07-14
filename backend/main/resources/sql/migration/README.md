# sql/migration — 이력 보관용 (더 이상 실행되지 않음)

이 디렉토리의 `applied/*.sql` 은 **Flyway 도입 이전에 손으로 적용하던 마이그레이션의 기록**이다.
내용은 모두 `db/migration/V1__baseline_schema.sql` 에 녹아 있으며, 지금은 **아무것도 자동 실행하지 않는다.**
(docker-compose 의 `docker-entrypoint-initdb.d` 마운트도 제거됨)

## 스키마를 바꾸려면

**여기에 파일을 추가하지 말 것.** 새 마이그레이션은 Flyway 가 읽는 경로에 버전을 붙여 만든다:

```
backend/main/resources/db/migration/V2__무엇을_바꾸는지.sql
```

앱이 뜰 때 Flyway 가 로컬·도커·CI 어디서든 같은 순서로 적용한다. 적용 후에는
`spring.jpa.hibernate.ddl-auto=validate` 가 엔티티와 대조하므로, 엔티티와 어긋나면 기동에 실패한다.

## 왜 이렇게 바뀌었나

스키마 사본이 4개(로컬 DB, 도커 DB, `000-baseline-schema.sql`, `.github/ci-schema.sql`)였고
손으로 맞추다 보니 **네 개가 전부 서로 다른 지점에서 어긋나 있었다.** 사본을 하나로 줄이려고 Flyway 를 도입했다.
