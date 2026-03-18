/**
 * 데모 모드 여부 확인
 * REACT_APP_DEMO_MODE=true 로 빌드 시 GitHub Pages 등 백엔드 없는 환경에서 동작
 */
export const isDemoMode = () =>
  process.env.REACT_APP_DEMO_MODE === 'true';
