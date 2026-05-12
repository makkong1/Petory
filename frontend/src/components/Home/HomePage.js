import React, { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { useAuth } from '../../contexts/AuthContext';
import { locationServiceApi } from '../../api/locationServiceApi';
import { meetupApi } from '../../api/meetupApi';
import { missingPetApi } from '../../api/missingPetApi';
import { boardApi } from '../../api/boardApi';

const TABS = [
  { key: 'service',   label: '주변서비스', domainColor: '#3B82F6' },
  { key: 'meetup',    label: '모임',       domainColor: '#10B981' },
  { key: 'missing',   label: '실종신고',   domainColor: '#EF4444' },
  { key: 'community', label: '커뮤니티',   domainColor: '#8B5CF6' },
];

const HomePage = ({ setActiveTab }) => {
  const { user } = useAuth();
  const isAdmin = user && (user.role === 'ADMIN' || user.role === 'MASTER');
  const nickname = user?.nickname || '사용자';

  const [activeTab, setActiveTabLocal] = useState('service');
  const [tabData, setTabData] = useState({});
  const [tabLoading, setTabLoading] = useState({});
  const [tabError, setTabError] = useState({});

  const fetchedTabsRef = React.useRef(new Set());

  const fetchTabData = useCallback(async (tabKey) => {
    if (fetchedTabsRef.current.has(tabKey)) return;
    fetchedTabsRef.current.add(tabKey);
    setTabLoading(prev => ({ ...prev, [tabKey]: true }));
    setTabError(prev => ({ ...prev, [tabKey]: false }));
    try {
      let items = [];
      if (tabKey === 'service') {
        const res = await locationServiceApi.searchPlaces({ sort: 'rating', size: 6 });
        items = res.data?.results || res.data || [];
      } else if (tabKey === 'meetup') {
        const res = await meetupApi.getNearbyMeetups(37.5665, 126.9780, 50, 6);
        items = res.data || [];
      } else if (tabKey === 'missing') {
        const res = await missingPetApi.list({ page: 0, size: 6, status: 'MISSING' });
        items = res.data?.boards || res.data || [];
      } else if (tabKey === 'community') {
        const res = await boardApi.getPopularBoards('WEEKLY');
        items = res.data?.boards || res.data || [];
      }
      setTabData(prev => ({ ...prev, [tabKey]: items.slice(0, 6) }));
    } catch {
      fetchedTabsRef.current.delete(tabKey); // allow retry on error
      setTabError(prev => ({ ...prev, [tabKey]: true }));
      setTabData(prev => ({ ...prev, [tabKey]: [] }));
    } finally {
      setTabLoading(prev => ({ ...prev, [tabKey]: false }));
    }
  }, []); // empty dep array — stable reference

  useEffect(() => {
    fetchTabData(activeTab);
  }, [activeTab, fetchTabData]);

  return (
    <PageWrapper>
      <PageContainer>
        {/* Tasks 2-7 will fill this in */}
      </PageContainer>
    </PageWrapper>
  );
};

export default HomePage;

/* ── Layout ─────────────────────────────────────────────────── */

const PageWrapper = styled.div`
  min-height: 100vh;
  background: ${props => props.theme.colors.surfaceSoft};
  display: flex;
  justify-content: center;
`;

const PageContainer = styled.div`
  width: 100%;
  max-width: 430px;
  min-height: 100vh;
  background: ${props => props.theme.colors.background};
  overflow-x: hidden;
  padding-bottom: 24px;
`;
