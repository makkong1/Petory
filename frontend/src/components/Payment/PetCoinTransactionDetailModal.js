import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { useTheme } from '../../contexts/ThemeContext';
import { paymentApi } from '../../api/paymentApi';

const TRANSACTION_TYPE_LABELS = {
  CHARGE: '충전',
  DEDUCT: '차감',
  PAYOUT: '지급',
  REFUND: '환불',
};

const PetCoinTransactionDetailModal = ({ transactionId, onClose }) => {
  const { theme } = useTheme();
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (transactionId) {
      fetchDetail();
    }
    return () => {};
  }, [transactionId]);

  const fetchDetail = async () => {
    setLoading(true);
    setError('');
    try {
      const data = await paymentApi.getTransactionDetail(transactionId);
      setDetail(data);
    } catch (err) {
      console.error('거래 상세 조회 실패:', err);
      setError('거래 상세를 불러오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return '-';
    const d = new Date(dateStr);
    return d.toLocaleDateString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  };

  const getAmountColor = (type) => {
    if (type === 'CHARGE' || type === 'PAYOUT' || type === 'REFUND') return theme?.colors?.primary || '#FF7E36';
    return theme?.colors?.text || '#333';
  };

  const getAmountPrefix = (type) => {
    if (type === 'CHARGE' || type === 'PAYOUT' || type === 'REFUND') return '+';
    return '-';
  };

  const getCounterpartyDescription = () => {
    if (!detail) return null;
    const { transactionType, counterpartyUsername, amount, relatedTitle } = detail;
    if (!counterpartyUsername) return null;

    switch (transactionType) {
      case 'DEDUCT':
        return `${counterpartyUsername}님에게 ${amount?.toLocaleString()}코인 결제`;
      case 'PAYOUT':
        return `${counterpartyUsername}님으로부터 ${amount?.toLocaleString()}코인 수령`;
      case 'REFUND':
        return `${counterpartyUsername}님과의 거래 취소로 ${amount?.toLocaleString()}코인 환불`;
      default:
        return null;
    }
  };

  return (
    <ModalOverlay onClick={onClose} theme={theme}>
      <ModalContent onClick={(e) => e.stopPropagation()} theme={theme}>
        <ModalHeader theme={theme}>
          <Title theme={theme}>거래 상세</Title>
          <CloseButton onClick={onClose} theme={theme}>✕</CloseButton>
        </ModalHeader>

        {error && <ErrorMessage theme={theme}>{error}</ErrorMessage>}

        {loading ? (
          <LoadingSection theme={theme}>불러오는 중...</LoadingSection>
        ) : detail ? (
          <DetailSection theme={theme}>
            <DetailRow>
              <DetailLabel theme={theme}>거래 유형</DetailLabel>
              <DetailValue theme={theme}>
                {TRANSACTION_TYPE_LABELS[detail.transactionType] || detail.transactionType}
              </DetailValue>
            </DetailRow>

            <DetailRow>
              <DetailLabel theme={theme}>금액</DetailLabel>
              <DetailValue
                theme={theme}
                $color={getAmountColor(detail.transactionType)}
                $bold
              >
                {getAmountPrefix(detail.transactionType)}{detail.amount?.toLocaleString()} 코인
              </DetailValue>
            </DetailRow>

            {detail.counterpartyUsername && (
              <DetailRow>
                <DetailLabel theme={theme}>거래 상대방</DetailLabel>
                <DetailValue theme={theme}>{detail.counterpartyUsername}</DetailValue>
              </DetailRow>
            )}

            {getCounterpartyDescription() && (
              <DetailRow>
                <DetailLabel theme={theme}>거래 내용</DetailLabel>
                <DetailValue theme={theme}>{getCounterpartyDescription()}</DetailValue>
              </DetailRow>
            )}

            {detail.relatedTitle && (
              <DetailRow>
                <DetailLabel theme={theme}>관련 서비스</DetailLabel>
                <DetailValue theme={theme}>{detail.relatedTitle}</DetailValue>
              </DetailRow>
            )}

            <DetailRow>
              <DetailLabel theme={theme}>거래 전 잔액</DetailLabel>
              <DetailValue theme={theme}>{detail.balanceBefore?.toLocaleString()} 코인</DetailValue>
            </DetailRow>

            <DetailRow>
              <DetailLabel theme={theme}>거래 후 잔액</DetailLabel>
              <DetailValue theme={theme}>{detail.balanceAfter?.toLocaleString()} 코인</DetailValue>
            </DetailRow>

            <DetailRow>
              <DetailLabel theme={theme}>거래 일시</DetailLabel>
              <DetailValue theme={theme}>{formatDate(detail.createdAt)}</DetailValue>
            </DetailRow>

            {detail.description && (
              <DetailRow>
                <DetailLabel theme={theme}>설명</DetailLabel>
                <DetailValue theme={theme}>{detail.description}</DetailValue>
              </DetailRow>
            )}
          </DetailSection>
        ) : null}
      </ModalContent>
    </ModalOverlay>
  );
};

export default PetCoinTransactionDetailModal;

const ModalOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: ${(props) => props.theme?.colors?.overlay || 'rgba(0, 0, 0, 0.5)'};
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2100;
  padding: 20px;
`;

const ModalContent = styled.div`
  background: ${(props) => props.theme?.colors?.surface || '#ffffff'};
  border-radius: 16px;
  width: 100%;
  max-width: 440px;
  max-height: 85vh;
  overflow-y: auto;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
`;

const ModalHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px;
  border-bottom: 1px solid ${(props) => props.theme?.colors?.border || '#e0e0e0'};
`;

const Title = styled.h2`
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: ${(props) => props.theme?.colors?.text || '#333'};
`;

const CloseButton = styled.button`
  background: none;
  border: none;
  font-size: 24px;
  color: ${(props) => props.theme?.colors?.textSecondary || '#666'};
  cursor: pointer;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  &:hover {
    background: ${(props) => props.theme?.colors?.surfaceHover || '#f5f5f5'};
  }
`;

const ErrorMessage = styled.div`
  padding: 12px 24px;
  margin: 0 24px;
  background: #fee;
  color: #c33;
  border-radius: 8px;
  font-size: 14px;
`;

const LoadingSection = styled.div`
  padding: 48px;
  text-align: center;
  color: ${(props) => props.theme?.colors?.textSecondary || '#666'};
`;

const DetailSection = styled.div`
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const DetailRow = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const DetailLabel = styled.span`
  font-size: 12px;
  color: ${(props) => props.theme?.colors?.textSecondary || '#666'};
`;

const DetailValue = styled.span`
  font-size: 15px;
  color: ${(props) => props.$color || props.theme?.colors?.text || '#333'};
  font-weight: ${(props) => (props.$bold ? 600 : 400)};
`;
