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

const careMapSubtitle = (raw) => {
  const bits = [];
  if (raw?.date) bits.push(String(raw.date).slice(0, 10));
  if (raw?.estimatedDurationMinutes != null) bits.push(`~${raw.estimatedDurationMinutes}분`);
  if (raw?.scheduleMode === 'FLEXIBLE_CHAT') bits.push('조율');
  const pet = raw?.petName || raw?.pet?.name || '';
  const head = bits.length ? bits.join(' · ') : '';
  if (!head && !pet) return '';
  return head && pet ? `${head} · ${pet}` : head || pet;
};

const toMapItem = (type, raw) => {
  const config = LAYER_CONFIG[type];
  const subtitle = {
    location: raw.category || raw.address || raw.roadAddress || '',
    meetup: raw.meetupDate
      ? `${raw.meetupDate.slice(0, 10)} · ${raw.currentParticipants ?? 0}/${raw.maxParticipants ?? 0}명`
      : `${raw.currentParticipants ?? 0}/${raw.maxParticipants ?? 0}명`,
    care: careMapSubtitle(raw),
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

// 줌 레벨(카카오맵 1~14) → 반환 개수 매핑
const ZOOM_LIMIT_TABLE = {
  location: { 4: 30, 5: 50, 6: 100, 7: 150, 8: 250, 9: 400, default: 500 },
  meetup:   { 4: 30, 5: 50, 6: 100, 7: 200, 8: 350, 9: 500, default: 800 },
  care:     { 4: 20, 5: 30, 6: 50,  7: 80,  8: 150, 9: 250, default: 400 },
};

const getLimitForLevel = (type, level) => {
  const table = ZOOM_LIMIT_TABLE[type];
  const key = level <= 4 ? 4 : level >= 10 ? 'default' : level;
  return table[key] ?? table['default'];
};

/**
 * 활성 탭 1개의 데이터만 조회해 공통 mapItem 배열로 반환
 * @param {number} mapLevel - 카카오맵 기준 줌 레벨 (1=최대확대, 14=전국). 기본값 7
 */
export const fetchActiveMapItems = async ({ type, lat, lng, radius, keyword, category, sort, mapLevel = 7 }) => {
  if (type === 'location') {
    const radiusKm = typeof radius === 'number' && Number.isFinite(radius) ? radius : 5;
    const res = await locationServiceApi.searchPlaces({
      latitude: lat,
      longitude: lng,
      radius: radiusKm * 1000, // km → m
      ...(keyword && { keyword }),
      ...(category && { category }),
      ...(sort && { sort }),
      size: getLimitForLevel('location', mapLevel),
    });
    const services = res?.data?.services ?? [];
    return services.map(r => toMapItem('location', r));
  }

  if (type === 'meetup') {
    const res = await meetupApi.getNearbyMeetups(lat, lng, radius, getLimitForLevel('meetup', mapLevel));
    const meetups = res?.data?.meetups ?? res?.data ?? [];
    return meetups.map(r => toMapItem('meetup', r));
  }

  if (type === 'care') {
    if (isDemoMode()) return [];
    const res = await careRequestApi.getNearby({ lat, lng, radius, limit: getLimitForLevel('care', mapLevel) });
    const careRequests = res?.data ?? [];
    return careRequests.map(r => toMapItem('care', r));
  }

  return [];
};
