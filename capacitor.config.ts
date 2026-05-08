import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.petory.app',
  appName: 'Petory',
  webDir: 'frontend/build',
  server: {
    // 개발 시: 로컬 서버 직접 가리킴 (핫리로드 가능)
    // 운영 배포 시: 이 블록 전체 제거하고 webDir 번들만 사용
    // url: 'http://192.168.1.XXX:3000',
    // cleartext: true,
  },
  android: {
    allowMixedContent: false,
    captureInput: true,
    webContentsDebuggingEnabled: false,
  },
  plugins: {
    Preferences: {
      group: 'PetoryStorage',
    },
  },
};

export default config;
