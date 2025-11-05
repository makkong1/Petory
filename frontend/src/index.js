import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';

// Passive event listener 경고 해결을 위한 전역 패치
// scroll-blocking 이벤트에 자동으로 passive: true 추가
if (typeof window !== 'undefined' && typeof EventTarget !== 'undefined') {
  const originalAddEventListener = EventTarget.prototype.addEventListener;
  
  EventTarget.prototype.addEventListener = function(type, listener, options) {
    // scroll-blocking 이벤트 목록
    const scrollBlockingEvents = ['touchstart', 'touchmove', 'wheel', 'mousewheel'];
    
    // scroll-blocking 이벤트이고 passive 옵션이 명시되지 않은 경우
    if (scrollBlockingEvents.includes(type)) {
      // options가 객체인 경우
      if (typeof options === 'object' && options !== null) {
        // passive가 명시적으로 false가 아니면 true로 설정
        if (options.passive === undefined) {
          options = { ...options, passive: true };
        }
      } 
      // options가 boolean인 경우 (capture만 지정)
      else if (typeof options === 'boolean') {
        options = { capture: options, passive: true };
      }
      // options가 없거나 undefined인 경우
      else {
        options = { passive: true };
      }
    }
    
    // 원본 메서드 호출
    return originalAddEventListener.call(this, type, listener, options);
  };
}

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
