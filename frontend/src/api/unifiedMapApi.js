import { locationServiceApi } from './locationServiceApi';
import { meetupApi } from './meetupApi';
import { careRequestApi } from './careRequestApi';
import { isDemoMode } from '../mock/isDemoMode';

export const LAYER_CONFIG = {
  location: {
    color: '#4A90D9',
    icon: '🏥',
    label: '주변서비스',
    zIndex: 100,
  },
  meetup: {
    color: '#52C41A',
    icon: '🐾',
    label: '모임',
    zIndex: 200,
  },
  care: {
    color: '#FAAD14',
    icon: '💛',
    label: '펫케어',
    zIndex: 300,
  },
};

const toMapItem = (type, raw) => {
  const config = LAYER_CONFIG[type];
  const subtitle = {
    location: raw.category || raw.address || raw.roadAddress || '',
    meetup: raw.meetupDate
      ? `${raw.meetupDate.slice(0, 10)} · ${raw.currentParticipants ?? 0}/${raw.maxParticipants ?? 0}명`
      : `${raw.currentParticipants ?? 0}/${raw.maxParticipants ?? 0}명`,
    care: raw.date
      ? `${String(raw.date).slice(0, 10)} · ${raw.petName || ''}`
      : raw.petName || '',
  }[type];

  return {
    // MapContainer용 필드
    idx: raw.idx,
    name: raw.name || raw.title || '',
    latitude: raw.latitude,
    longitude: raw.longitude,
    markerColor: config.color,
    // 통합 정보
    id: `${type}-${raw.idx}`,
    type,
    title: raw.name || raw.title || '',
    subtitle: subtitle || '',
    raw,
  };
};

/**
 * 활성 탭 1개의 데이터만 조회해 공통 mapItem 배열로 반환
 */
export const fetchActiveMapItems = async ({ type, lat, lng, radius, keyword, category }) => {
  if (type === 'location') {
    const res = await locationServiceApi.searchPlaces({
      latitude: lat,
      longitude: lng,
      radius: radius * 1000, // km → m
      ...(keyword && { keyword }),
      ...(category && { category }),
    });
    const services = res?.data?.services ?? [];
    return services.map(r => toMapItem('location', r));
  }

  if (type === 'meetup') {
    const res = await meetupApi.getNearbyMeetups(lat, lng, radius);
    const meetups = res?.data?.meetups ?? res?.data ?? [];
    return meetups.map(r => toMapItem('meetup', r));
  }

  if (type === 'care') {
    if (isDemoMode()) return [];
    const res = await careRequestApi.getNearby({ lat, lng, radius });
    const careRequests = res?.data ?? [];
    return careRequests.map(r => toMapItem('care', r));
  }

  return [];
};
