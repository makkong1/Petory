# macOS(맥북) 로컬에서 Docker·Compose 쓰기

**대상**: Apple Silicon(M1/M2/M3) 및 Intel 맥에서 레포를 클론해 로컬로 스택을 띄울 때  
**전제**: 프로덕션 **리눅스 서버** 배포 흐름은 [배포 프로세스](./06-deployment-process.md)와 동일하고, 여기서는 **개발용 맥**에서의 차이만 정리합니다.

---

## Redis만 Docker로 쓰는 경우

로컬에서 Redis 컨테이너만 띄우고 Spring Boot는 호스트에서 실행하는 흐름은 [Docker 설정 (로컬 Redis)](./02-docker-configuration.md#로컬-redis-docker-수동-실행)에 정리되어 있습니다. `spring.redis.*`와 맞춰 두면 됩니다.

---

## 사전 준비

### Docker Desktop for Mac

1. [Docker Desktop](https://www.docker.com/products/docker-desktop/) 설치 후 실행합니다.
2. 메뉴 **Settings → Resources**에서 CPU/메모리 할당(예: 메모리 4GB 이상)을 여유 있게 두면 MySQL·Spring 컨테이너가 덜 불안정합니다.
3. **Apple Silicon**: `mysql:8.0`, `redis:7-alpine`, `nginx:alpine`은 `linux/arm64`를 지원합니다. **주의**: `eclipse-temurin:17-{jdk,jre}-alpine`은 amd64 전용이라 M시리즈 맥에서 빌드가 아예 실패합니다(`no match for platform in manifest`) — 이 레포의 `Dockerfile`은 `-jammy`(Debian 기반) 태그를 씁니다. 다른 이미지를 새로 추가할 땐 `docker manifest inspect <image>`로 arm64 지원 여부를 먼저 확인하는 게 안전합니다.

### Compose 명령 (v2)

맥의 Docker Desktop에는 보통 **Docker Compose V2**(`docker compose`)가 포함됩니다. 문서 전반이 `docker-compose`(하이픈)로 적혀 있어도, 맥에서는 아래처럼 **둘 다** 시도해 보세요.

```bash
docker compose -f docker-compose.yml up -d
# 또는
docker-compose -f docker-compose.yml up -d
```

둘 중 하나만 동작하면 그쪽을 사용하면 됩니다.

### 프로젝트 경로

서버 문서의 `/opt/petory`는 **리눅스 서버** 기준입니다. 맥에서는 보통 예를 들어 다음처럼 **클론한 디렉터리**에서 작업합니다.

```bash
cd ~/project/Petory   # 실제 경로에 맞게 수정
```

---

## 환경 변수

로컬에서 호스트(bootRun)로 직접 돌릴 땐 `backend/main/resources/application.properties`(gitignore)로 맞춥니다.  
Docker Compose 전체 스택을 쓸 땐 레포 루트의 `.env.example`을 `.env`로 복사해서 채웁니다.

`.env` 권한은 제한해 둡니다.

```bash
chmod 600 .env
```

---

## 실행·중지 (개발용 Compose)

```bash
docker compose up --build -d   # 최초 실행이거나 Dockerfile/코드가 바뀌었을 때
docker compose up -d           # 이미지 변경 없이 재기동만 할 때
docker compose logs -f
docker compose down            # 컨테이너만 종료, 볼륨(DB 데이터)은 유지
docker compose down -v         # 볼륨까지 삭제 → 다음 up 때 DB가 baseline부터 새로 초기화됨
```

상세는 [02-docker-configuration.md](./02-docker-configuration.md) 참고.

---

## 맥에서 자주 나는 이슈

### 포트 이미 사용 중

리눅스용 `netstat -tulpn`과 달리, macOS에서는 예를 들어:

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
lsof -nP -iTCP:3306 -sTCP:LISTEN
```

로 점유 프로세스를 확인한 뒤, 로컬에서 띄운 MySQL/다른 앱이 있으면 종료하거나 `docker-compose.yml`의 포트 매핑을 바꿉니다 (예: `"3307:3306"`, `"6380:6379"`처럼 **호스트 쪽 포트만** 바꾸면 됨 — 컨테이너 간 통신은 서비스 이름 기반이라 영향 없음).

### 파일 공유·성능

소스를 볼륨 마운트할 때, **Docker Desktop → Settings → Resources → File sharing**에 프로젝트가 있는 경로가 포함돼 있는지 확인합니다. 기본적으로 `Users` 아래는 대부분 포함됩니다.

### `gradlew` 실행 권한 (호스트에서 빌드할 때)

윈도우에서 클론한 레포는 맥에서 `./gradlew`가 거부될 수 있습니다.

```bash
chmod +x gradlew
```

### CRLF 줄바꿈

`gradlew` 스크립트가 CRLF면 맥 터미널에서 오류가 납니다. 에디터에서 **LF**로 저장하거나 `dos2unix`로 변환합니다.

---

## 서버 배포 문서와의 관계

| 문서 | 내용 |
|------|------|
| [02-docker-configuration.md](./02-docker-configuration.md) | Dockerfile·compose 예시 — 맥에서도 동일하게 빌드 가능 |
| [06-deployment-process.md](./06-deployment-process.md) | **서버(Linux)** 기준 경로·SSH — 맥 로컬과 혼동하지 않기 |
| [08-troubleshooting.md](./08-troubleshooting.md) | 포트·네트워크 점검에 macOS 명령 병기 |

CI(GitHub Actions)는 **ubuntu** 러너에서 돌아가므로, 맥과 서버 환경이 달라도 파이프라인 자체는 동일합니다.
