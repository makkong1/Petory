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

### 엔티티 `@Table(name)`과 실제 테이블명 대소문자 불일치 (리눅스에서만 발생)

**문제**: 로컬 macOS에서는 멀쩡히 되던 기능이 도커(리눅스) 컨테이너에서 "Table 'AbcXyz' doesn't exist"로 실패  
**원인**: macOS MySQL은 기본적으로 `lower_case_table_names=2`(테이블명 대소문자 구분 안 함)인데, 리눅스 MySQL(도커 포함)은 `lower_case_table_names=0`(구분함)이 기본값. 엔티티의 `@Table(name="MissingPetBoard")`처럼 실제 저장된 테이블명(`missing_pet_board`)과 대소문자가 다르면 리눅스에서만 터짐  
**해결**: `@Table(name)`을 실제 DB 테이블명과 정확히 일치(소문자 snake_case)시킴. 확인 방법: `SHOW VARIABLES LIKE 'lower_case_table_names';`

---

자세한 내용은 각 도메인별 트러블슈팅 문서를 참고하세요.

