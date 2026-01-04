# 배포 전략 개요

## 📋 배포 전략 개요

Petory 프로젝트는 **Docker 컨테이너 기반 배포**와 **Nginx를 이용한 리버스 프록시** 방식을 채택합니다.

---

## 🎯 배포 목표

1. **고가용성**: 무중단 배포를 통한 서비스 중단 최소화
2. **확장성**: 컨테이너 기반으로 수평 확장 용이
3. **일관성**: 개발/스테이징/프로덕션 환경 일관성 유지
4. **자동화**: CI/CD를 통한 배포 자동화

---

## 🏗️ 배포 아키텍처

### 전체 구조

```
┌─────────────────────────────────────────────────────────────┐
│                        Production Server                     │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                    Nginx (Port 80, 443)              │  │
│  │  - SSL/TLS Termination                               │  │
│  │  - Static File Serving (Frontend)                    │  │
│  │  - Reverse Proxy (Backend API)                       │  │
│  │  - Load Balancing (Future)                           │  │
│  └────────────────┬───────────────────────┬─────────────┘  │
│                   │                       │                 │
│         ┌─────────▼─────────┐  ┌─────────▼─────────┐      │
│         │   Frontend        │  │    Backend        │      │
│         │   Container       │  │   Container       │      │
│         │   (React Build)   │  │  (Spring Boot)    │      │
│         │   Port: 3000      │  │   Port: 8080      │      │
│         └───────────────────┘  └─────────┬─────────┘      │
│                                          │                 │
│                          ┌───────────────┼───────────────┐ │
│                          │               │               │ │
│                 ┌────────▼──────┐ ┌──────▼──────┐       │ │
│                 │  MySQL        │ │   Redis     │       │ │
│                 │  Container    │ │  Container  │       │ │
│                 │  Port: 3306   │ │  Port: 6379 │       │ │
│                 └───────────────┘ └─────────────┘       │ │
│                                                          │ │
└──────────────────────────────────────────────────────────┘
```

### 컴포넌트 설명

#### 1. Nginx
- **역할**: 리버스 프록시 및 정적 파일 서빙
- **주요 기능**:
  - Frontend 정적 파일 서빙
  - Backend API 프록시 (`/api/*`)
  - SSL/TLS 종료
  - Gzip 압축
  - 캐싱 정책

#### 2. Frontend Container
- **기반 이미지**: `nginx:alpine`
- **내용**: React 빌드 결과물 (`/build`)
- **포트**: 3000 (내부)
- **외부 노출**: Nginx를 통해서만 접근

#### 3. Backend Container
- **기반 이미지**: `openjdk:17-jdk-slim`
- **애플리케이션**: Spring Boot JAR 파일
- **포트**: 8080 (내부)
- **외부 노출**: Nginx를 통해서만 접근

#### 4. MySQL Container
- **기반 이미지**: `mysql:8.0`
- **포트**: 3306 (내부)
- **볼륨**: 데이터 영속성 보장

#### 5. Redis Container
- **기반 이미지**: `redis:7-alpine`
- **포트**: 6379 (내부)
- **용도**: 캐싱, 알림 버퍼링

---

## 🔄 배포 프로세스

### 배포 흐름

```
1. 코드 Push (GitHub)
   ↓
2. GitHub Actions 트리거
   ↓
3. 빌드 (Gradle + npm)
   ↓
4. Docker 이미지 빌드
   ↓
5. Docker Hub/Registry Push
   ↓
6. 서버에서 이미지 Pull
   ↓
7. 기존 컨테이너 중지
   ↓
8. 새 컨테이너 시작 (Health Check)
   ↓
9. 배포 완료
```

### 무중단 배포 전략

#### Blue-Green 배포 (Future)
- 두 개의 동일한 환경을 구성
- 하나는 운영(Blue), 하나는 대기(Green)
- 새 버전을 Green에 배포 후 테스트
- Nginx 라우팅을 Green으로 전환
- 문제 발생 시 즉시 Blue로 롤백

#### 현재 전략 (Rolling Update)
- 기존 컨테이너 중지 → 새 컨테이너 시작
- Health Check를 통한 배포 검증
- 문제 발생 시 이전 이미지로 롤백

---

## 🌍 환경별 전략

### 개발 환경
- **목적**: 로컬 개발 및 테스트
- **도구**: Docker Compose
- **특징**:
  - Hot Reload 지원
  - 개발 도구 포함
  - 로그 레벨: DEBUG

### 스테이징 환경
- **목적**: 배포 전 검증
- **도구**: Docker Compose / Kubernetes (선택)
- **특징**:
  - 프로덕션과 동일한 구조
  - 테스트 데이터 사용
  - 로그 레벨: INFO

### 프로덕션 환경
- **목적**: 실제 서비스 운영
- **도구**: Docker + Docker Compose / Kubernetes
- **특징**:
  - 최적화된 설정
  - 모니터링 및 로깅
  - 로그 레벨: WARN, ERROR
  - SSL/TLS 인증서 적용

---

## 📊 리소스 할당 (예시)

### 최소 사양 (소규모)
- **CPU**: 2 Core
- **Memory**: 4GB
- **Storage**: 50GB SSD

### 권장 사양 (중규모)
- **CPU**: 4 Core
- **Memory**: 8GB
- **Storage**: 100GB SSD

### 구성 요소별 리소스
- **Backend**: 1-2GB RAM
- **MySQL**: 1-2GB RAM
- **Redis**: 512MB RAM
- **Frontend**: 100MB RAM

---

## 🔐 보안 고려사항

### 네트워크
- 컨테이너 간 통신은 내부 네트워크 사용
- 외부 노출 포트 최소화 (80, 443만 노출)
- 방화벽 규칙 설정

### 데이터베이스
- 비밀번호 환경 변수로 관리
- SSL/TLS 연결 (선택)
- 정기 백업

### 애플리케이션
- JWT 토큰 보안
- HTTPS 강제
- CORS 설정
- Rate Limiting

---

## 📈 확장성 전략

### 수직 확장 (Scale Up)
- 서버 리소스 증가
- 컨테이너 리소스 제한 조정

### 수평 확장 (Scale Out)
- Backend 컨테이너 다중화
- Nginx Load Balancing
- MySQL Master-Slave 구성
- Redis Cluster 구성

---

## 🔍 다음 단계

1. [Docker 설정](./02-docker-configuration.md) - Dockerfile 및 docker-compose 설정
2. [CI/CD 파이프라인](./03-cicd-pipeline.md) - 자동 배포 구축
3. [Nginx 설정](./04-nginx-configuration.md) - 리버스 프록시 구성
4. [환경 변수 관리](./05-environment-variables.md) - 보안 설정
5. [배포 프로세스](./06-deployment-process.md) - 실제 배포 가이드

