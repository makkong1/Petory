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
3. **Apple Silicon**: 대부분의 공식 이미지(`mysql:8.0`, `redis:7-alpine`, `openjdk:17` 등)는 `linux/arm64`를 지원합니다. 커스텀 이미지가 `amd64`만 있으면 에뮬레이션으로 느려질 수 있습니다.

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

로컬 개발은 주로 `backend/main/resources/application.properties`(gitignore일 수 있음)로 맞춥니다.  
레포 루트에 `.env.example`은 **없을 수 있음** — Docker Compose 전체 스택을 추가하면 그때 `.env` 패턴을 두면 됨.

서버에서 `.env`만 쓸 때는 권한을 제한합니다.

```bash
chmod 600 .env
```

---

## 실행·중지 (개발용 Compose)

`docker-compose.yml`이 **레포에 추가된 뒤**에만 아래가 해당합니다. 현재는 [02-docker-configuration.md](./02-docker-configuration.md) 참고.

```bash
docker compose up -d
docker compose logs -f
docker compose down -v   # 볼륨 삭제 시 데이터 초기화 주의
```

---

## 맥에서 자주 나는 이슈

### 포트 이미 사용 중

리눅스용 `netstat -tulpn`과 달리, macOS에서는 예를 들어:

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
lsof -nP -iTCP:3306 -sTCP:LISTEN
```

로 점유 프로세스를 확인한 뒤, 로컬에서 띄운 MySQL/다른 앱이 있으면 종료하거나 `docker-compose.yml`의 포트 매핑을 바꿉니다.

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
