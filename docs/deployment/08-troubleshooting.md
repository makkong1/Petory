# 트러블슈팅

## 📋 일반적인 문제 해결

**macOS(맥북) 로컬**: Docker Desktop, Compose 명령, `gradlew` 권한·줄바꿈 문제는 [macOS 로컬 가이드](./00-macos-local.md)를 먼저 확인하세요.

### 컨테이너 시작 실패

**문제**: 컨테이너가 시작되지 않음  
**해결**:
```bash
docker compose logs app
docker ps -a
```

### 포트 충돌

**문제**: 포트가 이미 사용 중  
**해결 (Linux 서버)**:
```bash
sudo netstat -tulpn | grep :8080
# 포트 변경 또는 기존 프로세스 종료
```

**해결 (macOS)**:
```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
# 필요 시 해당 PID 종료 또는 docker-compose의 ports 매핑 변경
```

### 데이터베이스 연결 오류

**문제**: Backend에서 MySQL 연결 실패  
**해결**:
```bash
docker exec petory-mysql mysqladmin ping
docker network inspect petory_default
```

### 메모리 부족

**문제**: Out of Memory 에러  
**해결**:
- **Docker Desktop(macOS)**: Settings → Resources에서 메모리 상향
- Docker 리소스 제한 확인
- JVM 메모리 설정 조정 (`JAVA_OPTS`)

### arm64(Apple Silicon Mac)에서 이미지 빌드 실패

**문제**: `docker compose up --build` 시 `no match for platform in manifest: not found`  
**원인**: `eclipse-temurin:17-{jdk,jre}-alpine` 이미지가 amd64만 배포되고 arm64 매니페스트가 없음  
**해결**: Dockerfile의 베이스 이미지를 Debian 기반 `-jammy` 태그로 교체 (`17-jdk-jammy`, `17-jre-jammy`). non-root 유저 생성 명령도 alpine 전용 `addgroup`/`adduser`에서 `groupadd`/`useradd`로 변경 필요.

### 새 MySQL 볼륨으로 기동 시 테이블이 하나도 없음

**문제**: `docker compose down -v` 등으로 볼륨을 지우고 새로 띄우면 앱이 "Table 'xxx' doesn't exist" 에러를 내며 스케줄러 등에서 계속 실패  
**원인**: `sql/migration/`에 있던 파일들이 전부 기존 스키마 위에 컬럼을 추가하는 증분 ALTER 스크립트였고, 완전히 빈 DB에서 실행하면 중간에 깨짐 (`spring.jpa.hibernate.ddl-auto=none`이라 Hibernate 자동 생성도 안 됨)  
**해결**: 로컬 DB 스키마를 `mysqldump --no-data`로 떠서 `sql/migration/000-baseline-schema.sql`로 고정, 기존 증분 파일은 `sql/migration/applied/`로 이동(MySQL이 하위 폴더는 스캔하지 않아 자동실행 대상에서 제외됨)

> **⚠️ 2026-07-13 이후 이 문제는 발생하지 않는다.** 위 해결책은 스키마 정의 파일을 손으로 관리하는 방식이라 결국 사본이 갈라졌다(→ 다음 항목). 지금은 **Flyway**가 앱 기동 시 `db/migration/V*.sql`을 적용하므로, 빈 볼륨으로 띄워도 스키마가 자동 생성된다.

### 스키마 사본이 갈라져 환경마다 다른 DB가 됨 (2026-07-13 해결)

**문제**: 도커 DB에서 유저 조회가 `ERROR 1054 Unknown column 'is_dormant'`로 실패. 로컬에서는 멀쩡함
**원인**: 스키마 사본이 4개(로컬 DB / 도커 DB / `000-baseline-schema.sql` / `.github/ci-schema.sql`)였고 전부 손으로 관리하다 보니 **넷이 서로 다른 지점에서 어긋남**. 마이그레이션을 한쪽에만 적용하는 일이 반복됨
**해결**: **Flyway 도입.** 정본을 `db/migration/V1__baseline_schema.sql` 하나로 통합하고, 앱 기동 시 로컬·도커·CI에 동일하게 적용. 여기에 `spring.jpa.hibernate.ddl-auto=validate`를 걸어 엔티티와 스키마가 어긋나면 **앱이 아예 기동하지 않도록** 함 (드리프트가 조용히 쌓이지 못하게)

### 엔티티 `@Table(name)`과 실제 테이블명 대소문자 불일치 (리눅스에서만 발생)

**문제**: 로컬 macOS에서는 멀쩡히 되던 기능이 도커(리눅스) 컨테이너에서 "Table 'AbcXyz' doesn't exist"로 실패  
**원인**: macOS MySQL은 기본적으로 `lower_case_table_names=2`(테이블명 대소문자 구분 안 함)인데, 리눅스 MySQL(도커 포함)은 `lower_case_table_names=0`(구분함)이 기본값. 엔티티의 `@Table(name="MissingPetBoard")`처럼 실제 저장된 테이블명(`missing_pet_board`)과 대소문자가 다르면 리눅스에서만 터짐  
**해결**: `@Table(name)`을 실제 DB 테이블명과 정확히 일치(소문자 snake_case)시킴. 확인 방법: `SHOW VARIABLES LIKE 'lower_case_table_names';`

---

자세한 내용은 각 도메인별 트러블슈팅 문서를 참고하세요.

