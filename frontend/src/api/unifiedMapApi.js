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

/**
 * 활성 탭 1개의 데이터만 조회해 공통 mapItem 배열로 반환
 *
 * [지도 반경검색 통일] 결과 상한은 더 이상 프론트가 정하지 않는다.
 * 예전엔 여기 ZOOM_LIMIT_TABLE(줌 레벨 → meetup 30~800 / care 20~400)과
 * LOCATION_RESULT_LIMIT=300 이 따로 있었는데, 쿼리가 읽을 행 수를 결정하는 건
 * 줌 레벨이 아니라 반경이다. 둘은 따로 움직여서(반경 5km 로 두고 지도만 축소하면
 * 레벨 12 → 상한 400) 상한이 쿼리와 무관한 값을 따라다녔다.
 * 이제 백엔드 NearbySearchPolicy 가 반경으로 상한을 정한다. 프론트는 반경만 보낸다.
 */
export const fetchActiveMapItems = async ({ type, lat, lng, radius, keyword, category, sort }) => {
  if (type === 'location') {
    const radiusKm = typeof radius === 'number' && Number.isFinite(radius) ? radius : 5;
    const res = await locationServiceApi.searchPlaces({
      latitude: lat,
      longitude: lng,
      radius: radiusKm * 1000, // km → m
      ...(keyword && { keyword }),
      ...(category && { category }),
      ...(sort && { sort }),
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
