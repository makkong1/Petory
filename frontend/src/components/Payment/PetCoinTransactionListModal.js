import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { useTheme } from '../../contexts/ThemeContext';
import { paymentApi } from '../../api/paymentApi';
import PetCoinTransactionDetailModal from './PetCoinTransactionDetailModal';

const TRANSACTION_TYPE_LABELS = {
  CHARGE: '충전',
  DEDUCT: '차감',
  PAYOUT: '지급',
  REFUND: '환불',
};

const PetCoinTransactionListModal = ({ onClose }) => {
  const { theme } = useTheme();
  const [transactions, setTransactions] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [selectedTransactionId, setSelectedTransactionId] = useState(null);

  useEffect(() => {
    fetchTransactions(page);
  }, [page]);

  const fetchTransactions = async (pageNum = 0) => {
    setLoading(true);
    setError('');
    try {
      const data = await paymentApi.getTransactions(pageNum, 20);
      setTransactions(data.content || []);
      setTotalElements(data.totalElements || 0);
      setTotalPages(data.totalPages || 0);
    } catch (err) {
      console.error('거래 내역 조회 실패:', err);
      setError('거래 내역을 불러오는데 실패했습니다.');
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
    });
  };

  const getAmountColor = (type) => {
    if (type === 'CHARGE' || type === 'PAYOUT' || type === 'REFUND') return theme?.colors?.primary || '#FF7E36';
    return theme?.colors?.textSecondary || '#666';
  };

  const getAmountPrefix = (type) => {
    if (type === 'CHARGE' || type === 'PAYOUT' || type === 'REFUND') return '+';
    return '-';
  };

  return (
    <ModalOverlay onClick={onClose} theme={theme}>
      <ModalContent onClick={(e) => e.stopPropagation()} theme={theme}>
        <ModalHeader theme={theme}>
          <Title theme={theme}>거래 내역</Title>
          <CloseButton onClick={onClose} theme={theme}>✕</CloseButton>
        </ModalHeader>

        {error && <ErrorMessage theme={theme}>{error}</ErrorMessage>}

        {loading ? (
          <LoadingSection theme={theme}>불러오는 중...</LoadingSection>
        ) : transactions.length === 0 ? (
          <EmptySection theme={theme}>거래 내역이 없습니다.</EmptySection>
        ) : (
          <>
            <TransactionList>
              {transactions.map((tx) => (
                <TransactionItem
                  key={tx.idx}
                  onClick={() => setSelectedTransactionId(tx.idx)}
                  theme={theme}
                >
                  <TransactionLeft>
                    <TransactionType theme={theme}>
                      {TRANSACTION_TYPE_LABELS[tx.transactionType] || tx.transactionType}
                    </TransactionType>
                    <TransactionDesc theme={theme}>
                      {tx.description || '-'}
                    </TransactionDesc>
                    <TransactionDate theme={theme}>{formatDate(tx.createdAt)}</TransactionDate>
                  </TransactionLeft>
                  <TransactionAmount
                    theme={theme}
                    $type={tx.transactionType}
                    $color={getAmountColor(tx.transactionType)}
                  >
                    {getAmountPrefix(tx.transactionType)}{tx.amount?.toLocaleString()} 코인
                  </TransactionAmount>
                </TransactionItem>
              ))}
            </TransactionList>

            {totalPages > 1 && (
              <Pagination theme={theme}>
                <PageButton
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                  theme={theme}
                >
                  이전
                </PageButton>
                <PageInfo theme={theme}>
                  {page + 1} / {totalPages} (총 {totalElements}건)
                </PageInfo>
                <PageButton
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                  theme={theme}
                >
                  다음
                </PageButton>
              </Pagination>
            )}
          </>
        )}

        <InfoSection theme={theme}>
          <InfoText theme={theme}>항목을 클릭하면 상세 정보를 확인할 수 있습니다.</InfoText>
        </InfoSection>
      </ModalContent>

      {selectedTransactionId && (
        <PetCoinTransactionDetailModal
          transactionId={selectedTransactionId}
          onClose={() => setSelectedTransactionId(null)}
        />
      )}
    </ModalOverlay>
  );
};

export default PetCoinTransactionListModal;

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
  z-index: 2000;
  padding: 20px;
`;

const ModalContent = styled.div`
  background: ${(props) => props.theme?.colors?.surface || '#ffffff'};
  border-radius: 16px;
  width: 100%;
  max-width: 560px;
  max-height: 85vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
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
  font-size: 24px;
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

const EmptySection = styled.div`
  padding: 48px;
  text-align: center;
  color: ${(props) => props.theme?.colors?.textSecondary || '#666'};
`;

const TransactionList = styled.div`
  overflow-y: auto;
  flex: 1;
  padding: 16px;
`;

const TransactionItem = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.2s;
  border-bottom: 1px solid ${(props) => props.theme?.colors?.border || '#eee'};
  &:hover {
    background: ${(props) => props.theme?.colors?.surfaceHover || '#f8f9fa'};
  }
`;

const TransactionLeft = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const TransactionType = styled.span`
  font-weight: 600;
  font-size: 15px;
  color: ${(props) => props.theme?.colors?.text || '#333'};
`;

const TransactionDesc = styled.span`
  font-size: 13px;
  color: ${(props) => props.theme?.colors?.textSecondary || '#666'};
`;

const TransactionDate = styled.span`
  font-size: 12px;
  color: ${(props) => props.theme?.colors?.textLight || '#999'};
`;

const TransactionAmount = styled.span`
  font-weight: 600;
  font-size: 16px;
  color: ${(props) => props.$color};
`;

const Pagination = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border-top: 1px solid ${(props) => props.theme?.colors?.border || '#e0e0e0'};
`;

const PageButton = styled.button`
  padding: 8px 16px;
  border: 1px solid ${(props) => props.theme?.colors?.border || '#e0e0e0'};
  border-radius: 8px;
  background: ${(props) => props.theme?.colors?.surface || '#fff'};
  color: ${(props) => props.theme?.colors?.text || '#333'};
  cursor: pointer;
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const PageInfo = styled.span`
  font-size: 14px;
  color: ${(props) => props.theme?.colors?.textSecondary || '#666'};
`;

const InfoSection = styled.div`
  padding: 12px 24px;
  background: ${(props) => props.theme?.colors?.surfaceElevated || '#f8f9fa'};
  border-top: 1px solid ${(props) => props.theme?.colors?.border || '#e0e0e0'};
`;

const InfoText = styled.div`
  font-size: 12px;
  color: ${(props) => props.theme?.colors?.textSecondary || '#666'};
  text-align: center;
`;
