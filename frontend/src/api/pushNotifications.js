/**
 * FCM 푸시 알림 초기화 (Capacitor 네이티브 앱 전용)
 * 웹 환경에서는 isCapacitor() === false → 모든 동작 생략
 */
import { createAuthAxios, API_ROOT } from './apiClient';

const api = createAuthAxios(API_ROOT);

const isCapacitor = () =>
  typeof window !== 'undefined' && !!(window.Capacitor?.isNativePlatform?.());

export async function initPushNotifications() {
  if (!isCapacitor()) return;

  try {
    const { PushNotifications } = await import('@capacitor/push-notifications');

    // 권한 요청
    const permission = await PushNotifications.requestPermissions();
    if (permission.receive !== 'granted') return;

    await PushNotifications.register();

    // 토큰 발급 시 백엔드에 저장
    PushNotifications.addListener('registration', async ({ value: token }) => {
      const deviceType = window.Capacitor.getPlatform() === 'ios' ? 'IOS' : 'ANDROID';
      try {
        await api.post('/fcm/token', { token, deviceType });
      } catch (_) {}
    });

    // 앱 실행 중 알림 수신 (foreground)
    PushNotifications.addListener('pushNotificationReceived', (notification) => {
      console.log('FCM 포그라운드 알림:', notification.title);
    });

    // 알림 탭해서 앱 열릴 때
    PushNotifications.addListener('pushNotificationActionPerformed', (action) => {
      console.log('FCM 알림 탭:', action.notification.title);
    });
  } catch (e) {
    console.warn('FCM 초기화 실패:', e);
  }
}

export async function removePushToken() {
  if (!isCapacitor()) return;
  try {
    const { PushNotifications } = await import('@capacitor/push-notifications');
    const { value: token } = await PushNotifications.getToken();
    if (token) {
      await api.delete('/fcm/token', { data: { token } });
    }
  } catch (error) {
    console.warn('FCM 토큰 삭제 실패:', error);
  }
}
