# 🚀 Petory 코드 흐름 빠른 파악 가이드

> **목적**: 프로젝트 코드 흐름을 빠르게 이해하기 위한 실전 가이드

---

## 📋 목차

1. [전체 아키텍처](#전체-아키텍처)
2. [코드 읽는 순서 (우선순위)](#코드-읽는-순서-우선순위)
3. [주요 흐름 상세 설명](#주요-흐름-상세-설명)
4. [도메인별 구조](#도메인별-구조)
5. [빠른 시작 체크리스트](#빠른-시작-체크리스트)

---

## 🏗️ 전체 아키텍처

### 기술 스택
- **Backend**: Spring Boot 3.5.7 (Java 17)
- **Frontend**: React 19
- **Database**: MySQL 8.0
- **Cache**: Redis
- **인증**: JWT (Spring Security)

### 아키텍처 패턴
```
Frontend (React) 
    ↓ HTTP Request (Axios)
    ↓ Authorization: Bearer {JWT}
Backend (Spring Boot)
    ↓ JwtAuthenticationFilter (인증 검증)
    ↓ SecurityConfig (인가 체크)
    ↓ Controller (요청 처리)
    ↓ Service (비즈니스 로직)
    ↓ Repository (데이터 접근)
    ↓ MySQL / Redis
```

---

## 📖 코드 읽는 순서 (우선순위)

### 🔥 1단계: 진입점 파악 (필수)

#### Backend 시작점
1. **`PetoryApplication.java`** ⭐
   - Spring Boot 메인 클래스
   - 전체 설정 활성화 (@EnableScheduling, @EnableCaching 등)

2. **`SecurityConfig.java`** ⭐
   - 인증/인가 설정
   - 어떤 API가 공개/보호되는지 확인
   - 경로: `backend/main/java/com/linkup/Petory/global/security/SecurityConfig.java`

3. **`JwtAuthenticationFilter.java`** ⭐
   - JWT 토큰 검증 필터
   - 모든 요청이 거치는 첫 번째 관문
   - 경로: `backend/main/java/com/linkup/Petory/filter/JwtAuthenticationFilter.java`

#### Frontend 시작점
1. **`index.js`** ⭐
   - React 앱 진입점
   - 경로: `frontend/src/index.js`

2. **`App.js`** ⭐⭐⭐
   - 전체 앱 구조
   - 라우팅 로직
   - 인증 상태 관리
   - 경로: `frontend/src/App.js`

3. **`AuthContext.js`** ⭐⭐
   - 인증 상태 관리 (Context API)
   - 로그인/로그아웃 로직
   - 경로: `frontend/src/contexts/AuthContext.js`

### 🔥 2단계: 인증 흐름 파악 (필수)

#### 로그인 흐름
```
1. 사용자가 로그인 폼 입력
   ↓
2. LoginForm.js → authApi.login()
   ↓
3. POST /api/auth/login
   ↓
4. AuthController.login() (SecurityConfig에서 permitAll)
   ↓
5. JWT 토큰 생성 → localStorage 저장
   ↓
6. AuthContext에서 사용자 정보 저장
   ↓
7. App.js에서 isAuthenticated 체크 → 메인 화면 표시
```

**핵심 파일**:
- `frontend/src/components/Auth/LoginForm.js`
- `frontend/src/api/authApi.js`
- `backend/.../user/controller/AuthController.java` (추정)

#### API 요청 흐름
```
1. 컴포넌트에서 API 호출
   ↓
2. authApi.js의 인터셉터가 토큰 자동 추가
   ↓
3. HTTP Request → Backend
   ↓
4. JwtAuthenticationFilter가 토큰 검증
   ↓
5. SecurityConfig가 권한 체크
   ↓
6. Controller → Service → Repository
   ↓
7. Response 반환
```

**핵심 파일**:
- `frontend/src/api/authApi.js` (인터셉터 설정)
- `backend/.../filter/JwtAuthenticationFilter.java`

### 🔥 3단계: 도메인별 구조 파악

각 도메인은 **동일한 패턴**을 따릅니다:

```
domain/
├── {domain-name}/
│   ├── controller/     # REST API 엔드포인트
│   ├── service/        # 비즈니스 로직
│   ├── repository/     # 데이터 접근
│   ├── entity/         # JPA 엔티티 (DB 테이블)
│   ├── dto/            # 데이터 전송 객체
│   └── converter/      # Entity ↔ DTO 변환
```

**예시: Board 도메인**
- `BoardController.java` → `/api/boards/**` 엔드포인트
- `BoardService.java` → 게시글 CRUD 로직
- `BoardRepository.java` → DB 쿼리
- `Board.java` → board 테이블 매핑

---

## 🔄 주요 흐름 상세 설명

### 1. 인증/인가 흐름

#### Frontend → Backend 요청
```javascript
// 1. API 호출 (예: 게시글 목록)
boardApi.getBoards()
  ↓
// 2. authApi.js 인터셉터가 토큰 추가
axios.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  config.headers.Authorization = `Bearer ${token}`;
})
  ↓
// 3. HTTP Request 전송
GET /api/boards
Headers: { Authorization: Bearer {token} }
```

#### Backend 인증 처리
```java
// 1. JwtAuthenticationFilter (모든 요청이 거침)
public void doFilterInternal(...) {
    String token = extractToken(request);
    if (token != null && jwtUtil.validateToken(token)) {
        // SecurityContext에 인증 정보 저장
        SecurityContextHolder.getContext().setAuthentication(...);
    }
    filterChain.doFilter(request, response);
}

// 2. SecurityConfig (권한 체크)
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "MASTER")
    .requestMatchers("/api/**").authenticated()
)

// 3. Controller 실행
@GetMapping("/api/boards")
public ResponseEntity<List<BoardDTO>> getBoards() {
    // 인증된 사용자만 도달 가능
}
```

### 2. 데이터 흐름 (CRUD 예시)

#### 게시글 조회 흐름
```
1. Frontend: CommunityBoard.js
   ↓ boardApi.getBoards()
   
2. Backend: BoardController.getBoards()
   ↓
   
3. BoardService.getAllBoards()
   ↓
   
4. BoardRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc()
   ↓
   
5. BoardService.mapBoardsWithReactionsBatch()
   - 반응(좋아요/싫어요) 정보 추가
   - 첨부파일 정보 추가
   ↓
   
6. BoardConverter.toDTO()
   - Entity → DTO 변환
   ↓
   
7. Response 반환 → Frontend
```

**핵심 파일**:
- `frontend/src/components/Community/CommunityBoard.js`
- `frontend/src/api/boardApi.js`
- `backend/.../board/controller/BoardController.java`
- `backend/.../board/service/BoardService.java`

### 3. 상태 관리 흐름

#### Frontend 상태 관리
```
1. Context API 사용
   - AuthContext: 인증 상태
   - ThemeContext: 다크모드/라이트모드

2. 로컬 상태 (useState)
   - 각 컴포넌트에서 관리
   - 예: 게시글 목록, 필터 상태 등

3. API 호출 → 상태 업데이트
   - useEffect로 데이터 로드
   - setState로 상태 업데이트
```

**핵심 파일**:
- `frontend/src/contexts/AuthContext.js`
- `frontend/src/contexts/ThemeContext.js`

---

## 📁 도메인별 구조

### 주요 도메인 목록

1. **board** (커뮤니티 게시판)
   - 게시글, 댓글, 반응(좋아요/싫어요)
   - 인기 게시글 계산

2. **care** (펫 케어 서비스)
   - 케어 요청, 지원, 매칭, 리뷰

3. **location** (위치 기반 서비스)
   - 동물병원, 펫샵 등 위치 정보
   - 공간 인덱스 활용

4. **user** (사용자)
   - 회원가입, 로그인, 프로필

5. **report** (신고)
   - 게시글/댓글 신고 처리

6. **notification** (알림)
   - Redis + MySQL 이중 저장

7. **statistics** (통계)
   - 관리자 대시보드 데이터

8. **file** (파일)
   - 첨부파일 업로드/다운로드

### 도메인 읽는 순서

각 도메인을 이해할 때는 다음 순서로 읽으세요:

```
1. Entity (데이터 구조 파악)
   ↓
2. DTO (API 입출력 구조 파악)
   ↓
3. Controller (어떤 API가 있는지 파악)
   ↓
4. Service (비즈니스 로직 파악)
   ↓
5. Repository (데이터 접근 방법 파악)
```

**예시: Board 도메인**
1. `Board.java` - 게시글 엔티티 구조
2. `BoardDTO.java` - API 응답 구조
3. `BoardController.java` - `/api/boards/**` 엔드포인트 확인
4. `BoardService.java` - 게시글 CRUD 로직
5. `BoardRepository.java` - 쿼리 메서드 확인

---

## ✅ 빠른 시작 체크리스트

### 첫날: 기본 흐름 파악
- [ ] `PetoryApplication.java` 읽기
- [ ] `SecurityConfig.java` 읽기 (어떤 API가 보호되는지)
- [ ] `App.js` 읽기 (프론트엔드 구조)
- [ ] `AuthContext.js` 읽기 (인증 흐름)
- [ ] 로그인 → API 호출 → 응답 흐름 추적

### 둘째날: 도메인 하나 깊이 파악
- [ ] Board 도메인 선택 (가장 단순)
- [ ] Entity → DTO → Controller → Service → Repository 순서로 읽기
- [ ] 프론트엔드에서 해당 도메인 사용하는 컴포넌트 찾기
- [ ] 전체 흐름 추적 (클릭 → API 호출 → DB 조회 → 응답)

### 셋째날: 다른 도메인들 파악
- [ ] 각 도메인의 Controller만 먼저 읽기 (어떤 기능이 있는지)
- [ ] 필요할 때 Service, Repository 깊이 파기

---

## 🎯 실전 팁

### 1. 디버깅으로 흐름 추적
```javascript
// Frontend: 브라우저 개발자 도구
// Network 탭에서 API 요청 확인
// Console에 로그 추가

// Backend: IDE 디버거 사용
// Controller, Service에 브레이크포인트 설정
```

### 2. 코드 검색 활용
- 특정 기능 찾기: `grep` 또는 IDE 검색
- 예: "게시글 생성" → `createBoard` 검색

### 3. API 엔드포인트 먼저 파악
- Controller를 먼저 읽으면 어떤 기능이 있는지 빠르게 파악 가능
- 각 엔드포인트의 역할을 이해한 후 Service 로직 파악

### 4. Entity 관계 파악
- `@ManyToOne`, `@OneToMany` 등 JPA 관계 파악
- 데이터베이스 ERD 그려보기 (선택사항)

### 5. 프론트엔드 컴포넌트 구조
```
components/
├── {Feature}/          # 기능별 폴더
│   ├── {Feature}Page.js      # 메인 페이지
│   ├── {Feature}Form.js      # 폼 컴포넌트
│   └── {Feature}Detail.js    # 상세 페이지
```

---

## 📚 추가 학습 자료

### Spring Boot
- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- Controller → Service → Repository 패턴

### React
- [React 공식 문서](https://react.dev)
- Context API, Hooks (useState, useEffect)

### JWT 인증
- JWT 토큰 구조 이해
- Spring Security 필터 체인

---

## 🔍 문제 해결 시 체크리스트

### API 호출이 안 될 때
1. [ ] 브라우저 Network 탭 확인 (요청이 전송되는지)
2. [ ] 토큰이 헤더에 포함되는지 확인
3. [ ] Backend 로그 확인 (요청이 도달하는지)
4. [ ] SecurityConfig에서 해당 경로가 허용되는지 확인

### 인증 오류가 날 때
1. [ ] JwtAuthenticationFilter 로그 확인
2. [ ] 토큰이 유효한지 확인
3. [ ] SecurityConfig 권한 설정 확인

### 데이터가 안 나올 때
1. [ ] Repository 쿼리 로그 확인
2. [ ] DB에 실제 데이터가 있는지 확인
3. [ ] Service 로직에서 필터링하는지 확인

---

## 📝 마무리

이 가이드를 따라가면 **3일 안에** 프로젝트의 전체 흐름을 파악할 수 있습니다.

**핵심은**:
1. 진입점부터 시작
2. 인증 흐름 먼저 이해
3. 도메인 하나씩 깊이 파악
4. 나머지는 필요할 때 찾아보기

**질문이 생기면**:
- 관련 도메인의 Controller 먼저 확인
- Service 로직에서 비즈니스 규칙 확인
- Entity에서 데이터 구조 확인

---

**작성일**: 2025년  
**작성자**: AI Assistant (Composer)

