import React, { useEffect, useState } from 'react';
import styled from 'styled-components';
import { notificationApi } from '../../api/notificationApi';
import { petNewsApi } from '../../api/petNewsApi';

/**
 * 지도 진입 시 「안 읽은 반려동물 소식」이 있으면 한 번 띄우는 배너.
 *
 * 노출 상태를 새로 만들지 않는다 — 기존 알림의 읽음 플래그를 그대로 쓴다.
 * 닫으면 읽음 처리되고, 다시 뜨지 않으며, 알림 목록에는 그대로 남는다.
 * (그래서 전용 소식 화면도 게시판도 없다.)
 *
 * 비로그인이면 알림 API 가 401 이다. 조용히 아무것도 그리지 않는다 — 지도는 비로그인도 본다.
 */
function PetNewsBanner() {
  const [notification, setNotification] = useState(null);
  const [news, setNews] = useState(null);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      try {
        const res = await notificationApi.getUnreadNotifications();
        const target = (res.data || []).find((n) => n.type === 'PET_NEWS');
        if (!target || cancelled) return;
        setNotification(target);

        if (target.relatedId) {
          const detail = await petNewsApi.get(target.relatedId);
          if (!cancelled) setNews(detail.data);
        }
      } catch (e) {
        // 비로그인·네트워크 오류. 배너는 부가 기능이라 지도를 막지 않는다.
      }
    };

    load();
    return () => { cancelled = true; };
  }, []);

  if (!notification) return null;

  const close = async () => {
    setNotification(null);
    try {
      await notificationApi.markAsRead(notification.idx);
    } catch (e) {
      // 읽음 처리 실패는 다음 진입에 다시 뜨는 것으로 끝난다. 사용자에게 알리지 않는다.
    }
  };

  return (
    <Banner>
      <Body>
        <Label>서울 반려동물 소식</Label>
        <Title>{news?.title || notification.content || notification.title}</Title>
        {news?.sourceUrl && (
          <Source href={news.sourceUrl} target="_blank" rel="noopener noreferrer">
            원문 보기 ↗
          </Source>
        )}
      </Body>
      <CloseButton type="button" onClick={close} aria-label="소식 닫기">×</CloseButton>
    </Banner>
  );
}

const Banner = styled.div`
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin: 0 0 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: ${({ theme }) => theme?.colors?.surface || '#f4f7ff'};
  border: 1px solid ${({ theme }) => theme?.colors?.border || '#d9e2ff'};
`;

const Body = styled.div`
  flex: 1;
  min-width: 0;
`;

const Label = styled.div`
  font-size: 12px;
  font-weight: 700;
  color: ${({ theme }) => theme?.colors?.primary || '#3b5bdb'};
  margin-bottom: 4px;
`;

const Title = styled.div`
  font-size: 14px;
  line-height: 1.4;
  color: ${({ theme }) => theme?.colors?.text || '#222'};
`;

const Source = styled.a`
  display: inline-block;
  margin-top: 6px;
  font-size: 13px;
  color: ${({ theme }) => theme?.colors?.primary || '#3b5bdb'};
`;

const CloseButton = styled.button`
  border: 0;
  background: transparent;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  color: ${({ theme }) => theme?.colors?.textSecondary || '#888'};
`;

export default PetNewsBanner;
