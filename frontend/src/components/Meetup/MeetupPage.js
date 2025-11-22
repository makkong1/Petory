import React, { useState, useEffect, useCallback, useRef } from 'react';
import styled from 'styled-components';
import { meetupApi } from '../../api/meetupApi';
import MapContainer from '../LocationService/MapContainer';

const DEFAULT_CENTER = { lat: 37.5665, lng: 126.9780 };
const DEFAULT_RADIUS = 5; // km

const MeetupPage = () => {
  const [meetups, setMeetups] = useState([]);
  const [selectedMeetup, setSelectedMeetup] = useState(null);
  const [participants, setParticipants] = useState([]);
  const [loading, setLoading] = useState(false);
  const [userLocation, setUserLocation] = useState(null);
  const [mapCenter, setMapCenter] = useState(DEFAULT_CENTER);
  const [radius, setRadius] = useState(DEFAULT_RADIUS);
  const [showList, setShowList] = useState(true);

  // 현재 위치 가져오기
  useEffect(() => {
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          const location = {
            lat: position.coords.latitude,
            lng: position.coords.longitude,
          };
          setUserLocation(location);
          setMapCenter(location);
        },
        (error) => {
          console.error('위치 정보 가져오기 실패:', error);
        }
      );
    }
  }, []);

  // 모임 목록 조회
  const fetchMeetups = useCallback(async () => {
    if (!mapCenter) return;

    setLoading(true);
    try {
      const response = await meetupApi.getNearbyMeetups(
        mapCenter.lat,
        mapCenter.lng,
        radius
      );
      setMeetups(response.data.meetups || []);
    } catch (error) {
      console.error('모임 조회 실패:', error);
      alert('모임을 불러오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  }, [mapCenter, radius]);

  // 지도 이동 시 모임 재조회
  const handleMapIdle = useCallback((mapInfo) => {
    if (mapInfo && mapInfo.lat && mapInfo.lng) {
      setMapCenter({
        lat: mapInfo.lat,
        lng: mapInfo.lng,
      });
    }
  }, []);

  useEffect(() => {
    fetchMeetups();
  }, [fetchMeetups]);

  // 참가자 목록 조회
  const fetchParticipants = async (meetupIdx) => {
    try {
      const response = await meetupApi.getParticipants(meetupIdx);
      setParticipants(response.data.participants || []);
    } catch (error) {
      console.error('참가자 목록 조회 실패:', error);
    }
  };

  // 모임 클릭 핸들러
  const handleMeetupClick = async (meetup) => {
    setSelectedMeetup(meetup);
    await fetchParticipants(meetup.idx);
  };

  // 마커 클릭 핸들러
  const handleMarkerClick = async (meetup) => {
    await handleMeetupClick(meetup);
  };

  // 날짜 포맷팅
  const formatDate = (dateString) => {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleString('ko-KR', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <Container>
      <Header>
        <Title>🐾 산책 모임</Title>
        <Controls>
          <RadiusSelect value={radius} onChange={(e) => setRadius(Number(e.target.value))}>
            <option value={1}>1km</option>
            <option value={3}>3km</option>
            <option value={5}>5km</option>
            <option value={10}>10km</option>
          </RadiusSelect>
          <ToggleButton onClick={() => setShowList(!showList)}>
            {showList ? '📋 리스트 숨기기' : '📋 리스트 보기'}
          </ToggleButton>
        </Controls>
      </Header>

      <ContentWrapper>
        <MapSection>
          <MapContainer
            services={meetups.map(m => ({
              idx: m.idx,
              name: m.title,
              latitude: m.latitude,
              longitude: m.longitude,
              address: m.location,
            }))}
            onServiceClick={handleMarkerClick}
            userLocation={userLocation}
            mapCenter={mapCenter}
            onMapIdle={handleMapIdle}
          />
        </MapSection>

        {showList && (
          <ListSection>
            <ListHeader>주변 모임 목록 ({meetups.length}개)</ListHeader>
            {loading ? (
              <LoadingText>로딩 중...</LoadingText>
            ) : meetups.length === 0 ? (
              <EmptyText>주변에 모임이 없습니다.</EmptyText>
            ) : (
              <MeetupList>
                {meetups.map((meetup) => (
                  <MeetupItem
                    key={meetup.idx}
                    onClick={() => handleMeetupClick(meetup)}
                    isSelected={selectedMeetup?.idx === meetup.idx}
                  >
                    <MeetupTitle>{meetup.title}</MeetupTitle>
                    <MeetupInfo>
                      <InfoItem>📍 {meetup.location}</InfoItem>
                      <InfoItem>🕐 {formatDate(meetup.date)}</InfoItem>
                      <InfoItem>
                        👥 {meetup.currentParticipants || 0}/{meetup.maxParticipants}명
                      </InfoItem>
                    </MeetupInfo>
                  </MeetupItem>
                ))}
              </MeetupList>
            )}
          </ListSection>
        )}
      </ContentWrapper>

      {selectedMeetup && (
        <ModalOverlay onClick={() => setSelectedMeetup(null)}>
          <ModalContent onClick={(e) => e.stopPropagation()}>
            <ModalHeader>
              <ModalTitle>{selectedMeetup.title}</ModalTitle>
              <CloseButton onClick={() => setSelectedMeetup(null)}>×</CloseButton>
            </ModalHeader>

            <ModalBody>
              <Section>
                <SectionTitle>📅 모임 일시</SectionTitle>
                <SectionContent>{formatDate(selectedMeetup.date)}</SectionContent>
              </Section>

              <Section>
                <SectionTitle>📍 모임 장소</SectionTitle>
                <SectionContent>{selectedMeetup.location}</SectionContent>
              </Section>

              {selectedMeetup.description && (
                <Section>
                  <SectionTitle>📝 모임 설명</SectionTitle>
                  <SectionContent>{selectedMeetup.description}</SectionContent>
                </Section>
              )}

              <Section>
                <SectionTitle>👥 참가자 ({participants.length}명)</SectionTitle>
                {participants.length === 0 ? (
                  <EmptyText>아직 참가자가 없습니다.</EmptyText>
                ) : (
                  <ParticipantsList>
                    {participants.map((p, index) => (
                      <ParticipantItem key={index}>
                        <ParticipantName>{p.username}</ParticipantName>
                        <ParticipantDate>
                          {new Date(p.joinedAt).toLocaleDateString('ko-KR')}
                        </ParticipantDate>
                      </ParticipantItem>
                    ))}
                  </ParticipantsList>
                )}
              </Section>

              <Section>
                <SectionTitle>📊 모임 정보</SectionTitle>
                <InfoGrid>
                  <InfoItem>
                    <Label>주최자:</Label>
                    <Value>{selectedMeetup.organizerName || '알 수 없음'}</Value>
                  </InfoItem>
                  <InfoItem>
                    <Label>참가 인원:</Label>
                    <Value>
                      {selectedMeetup.currentParticipants || 0}/{selectedMeetup.maxParticipants}명
                    </Value>
                  </InfoItem>
                  <InfoItem>
                    <Label>상태:</Label>
                    <Value>
                      {selectedMeetup.status === 'RECRUITING' ? '모집중' :
                       selectedMeetup.status === 'CLOSED' ? '마감' : '종료'}
                    </Value>
                  </InfoItem>
                </InfoGrid>
              </Section>
            </ModalBody>
          </ModalContent>
        </ModalOverlay>
      )}
    </Container>
  );
};

export default MeetupPage;

const Container = styled.div`
  width: 100%;
  height: calc(100vh - 80px);
  display: flex;
  flex-direction: column;
  background: ${props => props.theme.colors.background};
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 2rem;
  background: ${props => props.theme.colors.surface};
  border-bottom: 1px solid ${props => props.theme.colors.border};
`;

const Title = styled.h1`
  font-size: 1.5rem;
  font-weight: 700;
  color: ${props => props.theme.colors.text};
  margin: 0;
`;

const Controls = styled.div`
  display: flex;
  gap: 1rem;
  align-items: center;
`;

const RadiusSelect = styled.select`
  padding: 0.5rem 1rem;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  font-size: 0.9rem;
  cursor: pointer;
`;

const ToggleButton = styled.button`
  padding: 0.5rem 1rem;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: ${props => props.theme.colors.primary};
    color: white;
  }
`;

const ContentWrapper = styled.div`
  flex: 1;
  display: flex;
  overflow: hidden;
`;

const MapSection = styled.div`
  flex: 1;
  position: relative;
`;

const ListSection = styled.div`
  width: 350px;
  background: ${props => props.theme.colors.surface};
  border-left: 1px solid ${props => props.theme.colors.border};
  display: flex;
  flex-direction: column;
  overflow: hidden;
`;

const ListHeader = styled.div`
  padding: 1rem;
  font-weight: 600;
  border-bottom: 1px solid ${props => props.theme.colors.border};
  color: ${props => props.theme.colors.text};
`;

const MeetupList = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: 0.5rem;
`;

const MeetupItem = styled.div`
  padding: 1rem;
  margin-bottom: 0.5rem;
  background: ${props => props.isSelected ? props.theme.colors.primary + '20' : props.theme.colors.background};
  border: 1px solid ${props => props.isSelected ? props.theme.colors.primary : props.theme.colors.border};
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: ${props => props.theme.colors.primary + '10'};
    border-color: ${props => props.theme.colors.primary};
  }
`;

const MeetupTitle = styled.div`
  font-weight: 600;
  font-size: 1rem;
  margin-bottom: 0.5rem;
  color: ${props => props.theme.colors.text};
`;

const MeetupInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: ${props => props.theme.colors.textSecondary};
`;

const InfoItem = styled.div`
  display: flex;
  align-items: center;
  gap: 0.5rem;
`;

const LoadingText = styled.div`
  padding: 2rem;
  text-align: center;
  color: ${props => props.theme.colors.textSecondary};
`;

const EmptyText = styled.div`
  padding: 2rem;
  text-align: center;
  color: ${props => props.theme.colors.textSecondary};
`;

const ModalOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
`;

const ModalContent = styled.div`
  background: ${props => props.theme.colors.surface};
  border-radius: 12px;
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
`;

const ModalHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.5rem;
  border-bottom: 1px solid ${props => props.theme.colors.border};
`;

const ModalTitle = styled.h2`
  font-size: 1.5rem;
  font-weight: 700;
  color: ${props => props.theme.colors.text};
  margin: 0;
`;

const CloseButton = styled.button`
  background: none;
  border: none;
  font-size: 2rem;
  cursor: pointer;
  color: ${props => props.theme.colors.textSecondary};
  line-height: 1;

  &:hover {
    color: ${props => props.theme.colors.text};
  }
`;

const ModalBody = styled.div`
  padding: 1.5rem;
`;

const Section = styled.div`
  margin-bottom: 1.5rem;
`;

const SectionTitle = styled.h3`
  font-size: 1rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
  color: ${props => props.theme.colors.text};
`;

const SectionContent = styled.div`
  color: ${props => props.theme.colors.textSecondary};
  line-height: 1.6;
`;

const ParticipantsList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
`;

const ParticipantItem = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem;
  background: ${props => props.theme.colors.background};
  border-radius: 8px;
`;

const ParticipantName = styled.div`
  font-weight: 500;
  color: ${props => props.theme.colors.text};
`;

const ParticipantDate = styled.div`
  font-size: 0.85rem;
  color: ${props => props.theme.colors.textSecondary};
`;

const InfoGrid = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
`;

const Label = styled.span`
  font-weight: 600;
  color: ${props => props.theme.colors.text};
  margin-right: 0.5rem;
`;

const Value = styled.span`
  color: ${props => props.theme.colors.textSecondary};
`;

