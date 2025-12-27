import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';

const root = ReactDOM.createRoot(document.getElementById('root'));
// React.StrictMode는 개발 모드에서 useEffect를 2번 실행하여 중복 호출 발생
// 프로덕션 빌드에서는 자동으로 비활성화되지만, 개발 중 중복 호출 방지를 위해 주석 처리
root.render(
  <App />
  // <React.StrictMode>
  //   <App />
  // </React.StrictMode>
);
