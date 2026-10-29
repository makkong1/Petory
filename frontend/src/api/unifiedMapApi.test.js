import { fetchActiveMapItems } from './unifiedMapApi';
import { locationServiceApi } from './locationServiceApi';

jest.mock('./locationServiceApi', () => ({
  locationServiceApi: { searchPlaces: jest.fn() },
}));

const place = (idx, name, distance) => ({ idx, name, distance, latitude: 37, longitude: 127 });

// 「갈 만한 곳」 섹션은 중분류 두 개를 한 목록으로 합친다. 백엔드 category 는 단일 문자열
// 필터라 호출을 나누는데, 각 응답만 정렬돼 있어 그냥 이으면 순서가 깨진다.
describe('fetchActiveMapItems — category 배열', () => {
  beforeEach(() => {
    locationServiceApi.searchPlaces.mockReset();
  });

  it('카테고리마다 따로 호출한다', async () => {
    locationServiceApi.searchPlaces.mockResolvedValue({ data: { services: [] } });

    await fetchActiveMapItems({
      type: 'location', lat: 37, lng: 127, radius: 5,
      category: ['반려동반여행', '반려동물식당카페'],
    });

    expect(locationServiceApi.searchPlaces).toHaveBeenCalledTimes(2);
    const sent = locationServiceApi.searchPlaces.mock.calls.map(([args]) => args.category);
    expect(sent).toEqual(['반려동반여행', '반려동물식당카페']);
  });

  it('합친 뒤 거리순으로 다시 세운다', async () => {
    locationServiceApi.searchPlaces
      .mockResolvedValueOnce({ data: { services: [place(1, '먼여행지', 900)] } })
      .mockResolvedValueOnce({ data: { services: [place(2, '가까운카페', 100)] } });

    const items = await fetchActiveMapItems({
      type: 'location', lat: 37, lng: 127, radius: 5,
      category: ['반려동반여행', '반려동물식당카페'],
    });

    expect(items.map(i => i.title)).toEqual(['가까운카페', '먼여행지']);
  });

  it('정렬 기준이 거리가 아니면 그 기준으로 세운다', async () => {
    locationServiceApi.searchPlaces
      .mockResolvedValueOnce({ data: { services: [{ ...place(1, '저평점', 100), rating: 2 }] } })
      .mockResolvedValueOnce({ data: { services: [{ ...place(2, '고평점', 900), rating: 5 }] } });

    const items = await fetchActiveMapItems({
      type: 'location', lat: 37, lng: 127, radius: 5, sort: 'rating',
      category: ['반려동반여행', '반려동물식당카페'],
    });

    expect(items.map(i => i.title)).toEqual(['고평점', '저평점']);
  });

  it('같은 장소가 두 응답에 다 들어오면 한 번만 남긴다', async () => {
    locationServiceApi.searchPlaces
      .mockResolvedValueOnce({ data: { services: [place(7, '겹친곳', 100)] } })
      .mockResolvedValueOnce({ data: { services: [place(7, '겹친곳', 100)] } });

    const items = await fetchActiveMapItems({
      type: 'location', lat: 37, lng: 127, radius: 5,
      category: ['반려동반여행', '반려동물식당카페'],
    });

    expect(items).toHaveLength(1);
  });

  it('단일 문자열 category 는 한 번만 호출하고 백엔드 순서를 그대로 둔다', async () => {
    locationServiceApi.searchPlaces.mockResolvedValue({
      data: { services: [place(1, '먼곳', 900), place(2, '가까운곳', 100)] },
    });

    const items = await fetchActiveMapItems({
      type: 'location', lat: 37, lng: 127, radius: 5, category: '동물병원',
    });

    expect(locationServiceApi.searchPlaces).toHaveBeenCalledTimes(1);
    expect(items.map(i => i.title)).toEqual(['먼곳', '가까운곳']);
  });
});
