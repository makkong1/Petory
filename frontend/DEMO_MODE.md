# Petory 데모 모드 (GitHub Pages 배포용)

백엔드 없이 프론트엔드만 동작하는 데모 모드입니다. 포트폴리오 GitHub Pages 배포 시 사용합니다.

## 사용 방법

### 1. 데모 빌드

```bash
cd frontend
npm run build:demo
```

`build/` 폴더에 데모용 빌드 결과가 생성됩니다.

### 2. 로컬에서 데모 확인

```bash
npx serve -s build
```

브라우저에서 `http://localhost:3000` 접속 후 확인합니다.

### 3. 포트폴리오에 복사 후 배포

1. `frontend/` 폴더 전체를 포트폴리오 프로젝트로 복사
2. 포트폴리오에서 `npm run build:demo` 실행
3. `build/` 폴더를 GitHub Pages에 배포

또는 Petory에서 빌드한 `build/` 폴더를 그대로 포트폴리오 배포 경로에 복사해도 됩니다.

## 데모 모드 동작

- **로그인**: 아무 아이디/비밀번호로 로그인 가능 (예: `demo` / `demo`)
- **데이터**: Mock 데이터로 커뮤니티, 실종 제보, 케어 요청, 모임, 위치 서비스 등 표시
- **쓰기 작업**: 생성/수정/삭제는 Mock 응답만 반환 (실제 저장 안 됨)
- **소셜 로그인**: 데모 모드에서는 비활성화

## 환경 변수

`REACT_APP_DEMO_MODE=true` 로 빌드 시 데모 모드가 활성화됩니다.
`build:demo` 스크립트가 자동으로 이 값을 설정합니다.
