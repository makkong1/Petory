# 배포 전 보안 체크리스트 (Fly.io 기준)

> 2026-07-18 작성. 실제 코드(`SecurityConfig`, `application-prod.properties`, `docker-compose.yml`, `nginx/nginx.conf`, `FirebaseConfig`)를 전수 확인한 결과 기준.
> "지금 로컬/도커에서는 안전하지만, 인터넷에 공개하는 순간 문제가 되는 것"과 "옮기는 과정에서 새로 생기는 문제"를 구분해서 정리한다.
>
> Fly.io 선택 근거와 배포 절차 자체는 이 문서 범위 밖 — 여기서는 **배포 버튼 누르기 전에 반드시 확인할 것**만 다룬다.

## 사용법

- 🔴 **차단**: 이거 안 고치고 배포하면 안 됨
- 🟡 **권장**: 데모/포트폴리오 배포는 통과 가능, 실사용자 받기 전엔 필수
- 🟢 **인지**: 알고 있으면 됨 (면접에서 "알고 있는 한계"로 말할 소재)

---

## 🔴 차단 — 배포 전 필수 수정

### 1. CORS 전면 개방 해제

`SecurityConfig.corsConfigurationSource()`:

```java
configuration.setAllowedOriginPatterns(java.util.Arrays.asList("*")); // 모든 origin 허용
// TODO(운영): 아래 주석 해제 후 위 줄 제거
// configuration.setAllowedOriginPatterns(java.util.Arrays.asList(
//     "https://your-domain.com", ...
configuration.setAllowCredentials(true);
```

`allowedOriginPatterns("*")` + `allowCredentials(true)` 조합은 임의의 악성 사이트가 방문자의 브라우저를 통해 이 API를 자격 증명 포함으로 호출할 수 있게 한다. 코드에 이미 TODO로 정답이 주석 처리되어 있음 — **배포 도메인 확정 즉시 주석 해제하고 `*` 줄 제거.**

- [ ] `setAllowedOriginPatterns`를 실제 배포 도메인으로 제한
- [ ] `setAllowedMethods("*")`도 실제 쓰는 메서드 목록으로 축소 (같은 자리)

### 2. `/actuator/**`, `/admin-ui/**` 노출 재점검

`SecurityConfig`:

```java
.requestMatchers("/actuator/**").permitAll()   // 모니터링 엔드포인트 (로컬 전용)
.requestMatchers("/admin-ui/**").permitAll()   // Spring Boot Admin UI (로컬 전용)
```

지금 이게 안전한 유일한 이유는 `nginx/nginx.conf`의 `location /actuator/ { deny all; }` 블록이 외부 요청을 막아주기 때문이다 (`docs/interview/concepts/db-infra/05_네트워크_리버스프록시.md` §4). **Fly.io로 옮기면서 nginx 구성이 바뀌면 이 방어선이 사라진다** — 특히 Fly가 TLS를 종료하고 nginx를 얇게 만들거나 제거하는 구성을 택하면 두 경로가 인터넷에 그대로 열린다.

`application-prod.properties`가 `management.endpoints.web.exposure.include=health,info,prometheus`로 최소화는 해뒀지만, prometheus 메트릭에는 내부 구조 정보가 담긴다.

- [ ] Fly 구성에서도 nginx `deny all` 블록을 유지하거나, `SecurityConfig`에서 `permitAll`을 `hasRole("ADMIN")` 등으로 교체
- [ ] `/admin-ui/**` (Spring Boot Admin)는 외부 노출 자체를 차단

### 3. 내부 서비스 포트 노출 제거

`docker-compose.yml`이 로컬 개발 편의로 열어둔 호스트 포트:

```yaml
mysql:      ports: ["3307:3306"]
redis:      ports: ["6380:6379"]
nlp-server: ports: ["8000:8000"]
app:        ports: ["8080:8080"]
```

Fly.io에서 `[build.compose]`로 이 파일을 그대로 쓸 경우, **외부 노출은 nginx(443) 하나만** `[[services]]`로 선언하고 mysql/redis/nlp-server/app은 서비스 선언 자체를 하지 않아야 한다. 특히 MySQL은 `petory_app` 계정 비밀번호만으로 전체 데이터에 접근 가능하고, Redis는 `requirepass` 하나가 전부다.

- [ ] Fly 배포 구성에서 mysql/redis/nlp-server/app 외부 포트 미노출 확인
- [ ] 배포 후 외부에서 `nc -zv <app>.fly.dev 3306` 등으로 실제 차단 검증

### 4. 시크릿을 Fly secrets로 이전 (파일 업로드 금지)

`.env`, `application.properties`는 gitignore 되어 있고 **git 이력에도 없음** (`git ls-files`로 확인 완료 — 커밋된 적 없음). 이 상태를 유지한 채:

- [ ] `fly secrets set`으로 등록: `DB_ROOT_PASSWORD`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `GOOGLE_CLIENT_ID/SECRET`, `NAVER_CLIENT_ID/SECRET`, `NAVER_MAP_CLIENT_ID/SECRET`, `MAIL_USERNAME/PASSWORD`, `FRONTEND_URL`
- [ ] `.env` 파일을 이미지에 COPY하지 않는지 Dockerfile 확인 (compose의 `env_file: .env`는 로컬 전용 — Fly에서는 secrets가 환경변수로 주입됨)
- [ ] **JWT_SECRET은 배포용으로 새로 생성** — 로컬 개발에서 쓰던 값을 그대로 올리지 말 것 (여러 명이 로컬 세팅을 공유했을 수 있는 값)
- [ ] OAuth2 리다이렉트 URI를 Google/Naver 콘솔에서 배포 도메인으로 재등록

### 5. Firebase 서비스 계정 키 주입 방식 변경

`FirebaseConfig`는 `firebase.service-account.path`로 **JSON 파일 경로**를 읽는다. Fly secrets는 환경변수만 지원하므로 파일이 자동으로 생기지 않는다.

- [ ] JSON을 base64로 인코딩해 시크릿으로 넣고, 컨테이너 entrypoint에서 파일로 복원하는 방식 적용 (또는 `GoogleCredentials`를 환경변수 스트림에서 읽도록 `FirebaseConfig` 수정)
- [ ] 미적용 시 동작 확인: 현재 코드는 키가 없으면 FCM만 비활성화하고 기동은 됨 (`FirebaseConfig`의 `log.warn` 경로) — 데모 배포에서는 이 상태로 두는 것도 선택지

### 6. TLS 이중 종료 정리

현재 `nginx.conf`는 자체 인증서(`ssl_certificate` + certbot 경로)로 443을 직접 종료한다. Fly.io는 커스텀 도메인에 자동 인증서를 발급하고 **Fly edge에서 TLS를 종료**한다. 둘 다 켜면 충돌.

- [ ] `nginx.conf`의 443 서버 블록 제거, nginx는 내부 80 평문만 수신
- [ ] 이때 **보안 헤더(`X-Frame-Options`, `X-Content-Type-Options` 등)와 `/actuator/` 차단 블록은 80 블록으로 옮겨서 유지** — 443 블록을 지우면서 같이 날리지 않도록 주의
- [ ] HTTP→HTTPS 리다이렉트는 Fly의 `force_https = true`로 대체

---

## 🟡 권장 — 실사용자 받기 전 필수

### 7. 로그인 브루트포스 방어 부재

코드 전체에 로그인 시도 제한/계정 잠금 관련 구현이 없다 (grep으로 확인). 공개 배포되면 `/api/auth/**`는 `permitAll`이라 무제한 비밀번호 대입이 가능하다.

- 방향: 로그인 실패 횟수를 Redis에 `login_fail:{loginId}` TTL 카운터로 기록, N회 초과 시 일정 시간 차단. 이미 Redis 인프라와 이메일 인증 TTL 패턴(`08_Redis_캐시.md`)이 있으므로 같은 방식 재사용 가능.

### 8. Refresh Token 평문 저장 + Rotation 미적용

`Users.refreshToken` 컬럼에 토큰 원문이 저장된다 (`09_보안_JWT_인증.md` §2). DB가 유출되면 유효한 Refresh Token이 그대로 노출.

- 방향: 저장 시 SHA-256 해시로 저장하고 조회 시 해시 비교. Rotation(갱신 시 새 Refresh Token 발급 + 기존 무효화)도 함께.
- 지금 급하지 않은 이유: DB 자체가 외부 비노출(체크 3번)이고 Refresh TTL이 1일로 짧음.

### 9. SSE/WebSocket 토큰이 쿼리 파라미터로 전달됨

`EventSource`가 커스텀 헤더를 못 보내는 브라우저 제약 때문에 `?token=...` 방식 사용 (`06_실시간통신_SSE_WebSocket.md`). 토큰이 access log·브라우저 히스토리에 남는다.

- 완화: Fly/nginx 액세스 로그에서 쿼리스트링 마스킹, 또는 짧은 TTL의 일회용 SSE 전용 티켓 발급 방식으로 교체.
- Access Token TTL이 15분이라 노출 창이 짧은 점은 이미 완화 요소.

### 10. Rate Limiting (인프라 레벨)

`nginx.conf`에 `limit_req_zone` 미적용, 관리자 IP 화이트리스트는 주석 상태 (`db-infra/05` §5). 애플리케이션 레벨 인증(`@PreAuthorize`)만으로 막고 있는 현재 상태를 인지하고 배포.

---

## 🟢 인지 — 알고 배포하면 되는 것

| 항목 | 현재 상태 | 왜 지금은 괜찮은가 |
|---|---|---|
| 비밀번호 해싱 | `BCryptPasswordEncoder` ✅ | 표준 방식, 조치 불요 |
| DB 연결 암호화 | `useSSL=true&requireSSL=true` ✅ | prod 설정에 이미 있음 |
| JWT 필터의 제재 계정 검사 | `isUsableAccount`로 이미 검사 ✅ | 4차 점검(2026-07-17)에서 확인 |
| 브랜치 diff 보안 리뷰 | 2회 실시(2026-07-18), 신규 취약점 0건 | native query 전부 파라미터 바인딩, IDOR 2건은 오히려 이번 브랜치에서 수리됨 |
| dev 프로필 이메일 인증 스킵 | `skip-in-dev` — prod에서는 `false` | `SPRING_PROFILES_ACTIVE=prod` 확인만 |
| MySQL 데이터 영속성 | Fly volume 필요 (`mysql_data`) | 볼륨 없이 배포하면 재시작마다 데이터 소실 — 보안은 아니지만 배포 사고 1순위 |

---

## 배포 직전 최종 확인 순서

```
1. SecurityConfig CORS 수정 (체크 1) → compileJava
2. nginx.conf TLS/헤더 정리 (체크 6) + actuator 차단 유지 (체크 2)
3. compose 포트 노출 정리 (체크 3)
4. fly secrets set 전체 등록 + JWT_SECRET 신규 생성 (체크 4)
5. fly deploy 후 외부에서:
   - curl https://<domain>/actuator/health   → 403 또는 차단 확인
   - curl https://<domain>/admin-ui/         → 차단 확인
   - nc -zv <domain> 3306 / 6379 / 8000 / 8080 → 전부 실패 확인
   - 브라우저 개발자도구에서 Access-Control-Allow-Origin이 배포 도메인만인지 확인
6. 로그인 → 게시글 작성 → 채팅(WebSocket wss://) → 알림(SSE) 순으로 스모크 테스트
```
