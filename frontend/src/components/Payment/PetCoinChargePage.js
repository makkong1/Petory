import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { useTheme } from '../../contexts/ThemeContext';
import { paymentApi } from '../../api/paymentApi';

const PetCoinChargePage = ({ onClose }) => {
  const { theme } = useTheme();
  const [balance, setBalance] = useState(0);
  const [loading, setLoading] = useState(false);
  const [customAmount, setCustomAmount] = useState('');
  const [selectedAmount, setSelectedAmount] = useState(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // 코인 단위: 1코인 = 100원
  const COIN_RATE = 100;

  // 충전 옵션 (코인 단위)
  const chargeOptions = [
    { coins: 10, label: '10코인', price: 1000 },
    { coins: 50, label: '50코인', price: 5000 },
    { coins: 100, label: '100코인', price: 10000 },
    { coins: 500, label: '500코인', price: 50000 },
  ];

  useEffect(() => {
    fetchBalance();
  }, []);

  const fetchBalance = async () => {
    try {
      const data = await paymentApi.getBalance();
      setBalance(data.balance || 0);
    } catch (err) {
      console.error('잔액 조회 실패:', err);
      setError('잔액을 불러오는데 실패했습니다.');
    }
  };

  const handleCharge = async (coins) => {
    if (!coins || coins <= 0) {
      setError('충전할 코인 수를 입력해주세요.');
      return;
    }

    setLoading(true);
    setError('');
    setSuccess('');

    try {
      await paymentApi.chargeCoins(coins, `코인 충전 - ${coins}코인`);
      setSuccess(`${coins}코인이 충전되었습니다!`);
      setSelectedAmount(null);
      setCustomAmount('');
      await fetchBalance();
      
      // 2초 후 성공 메시지 제거
      setTimeout(() => {
        setSuccess('');
      }, 2000);
    } catch (err) {
      console.error('충전 실패:', err);
      const errorMessage = err.response?.data?.message || err.message || '충전에 실패했습니다.';
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const handleOptionClick = (coins) => {
    setSelectedAmount(coins);
    setCustomAmount('');
    setError('');
  };

  const handleCustomCharge = () => {
    const coins = parseInt(customAmount, 10);
    if (isNaN(coins) || coins <= 0) {
      setError('올바른 코인 수를 입력해주세요.');
      return;
    }
    handleCharge(coins);
  };

  return (
    <ModalOverlay onClick={onClose} theme={theme}>
      <ModalContent onClick={(e) => e.stopPropagation()} theme={theme}>
        <ModalHeader theme={theme}>
          <Title theme={theme}>펫코인 충전</Title>
          <CloseButton onClick={onClose} theme={theme}>✕</CloseButton>
        </ModalHeader>

        <BalanceSection theme={theme}>
          <BalanceLabel theme={theme}>현재 잔액</BalanceLabel>
          <BalanceAmount theme={theme}>{balance.toLocaleString()} 코인</BalanceAmount>
        </BalanceSection>

        <ChargeSection theme={theme}>
          <SectionTitle theme={theme}>충전할 코인 선택</SectionTitle>
          <ChargeOptions>
            {chargeOptions.map((option) => (
              <ChargeOption
                key={option.coins}
                selected={selectedAmount === option.coins}
                onClick={() => handleOptionClick(option.coins)}
                theme={theme}
              >
                <CoinAmount theme={theme}>{option.label}</CoinAmount>
                <Price theme={theme}>{option.price.toLocaleString()}원</Price>
              </ChargeOption>
            ))}
          </ChargeOptions>

          <Divider theme={theme}>
            <span>또는</span>
          </Divider>

          <CustomInputSection theme={theme}>
            <CustomInputLabel theme={theme}>직접 입력</CustomInputLabel>
            <CustomInputWrapper>
              <CustomInput
                type="number"
                placeholder="코인 수 입력"
                value={customAmount}
                onChange={(e) => {
                  setCustomAmount(e.target.value);
                  setSelectedAmount(null);
                  setError('');
                }}
                min="1"
                theme={theme}
              />
              <CoinUnit theme={theme}>코인</CoinUnit>
              {customAmount && (
                <PriceDisplay theme={theme}>
                  = {parseInt(customAmount || 0) * COIN_RATE}원
                </PriceDisplay>
              )}
            </CustomInputWrapper>
            <CustomChargeButton
              onClick={handleCustomCharge}
              disabled={!customAmount || loading}
              theme={theme}
            >
              충전하기
            </CustomChargeButton>
          </CustomInputSection>
        </ChargeSection>

        {selectedAmount && (
          <SelectedOptionSection theme={theme}>
            <SelectedInfo theme={theme}>
              {chargeOptions.find((opt) => opt.coins === selectedAmount)?.label} (
              {chargeOptions.find((opt) => opt.coins === selectedAmount)?.price.toLocaleString()}원)
            </SelectedInfo>
            <ChargeButton
              onClick={() => handleCharge(selectedAmount)}
              disabled={loading}
              theme={theme}
            >
              {loading ? '충전 중...' : '충전하기'}
            </ChargeButton>
          </SelectedOptionSection>
        )}

        {error && <ErrorMessage theme={theme}>{error}</ErrorMessage>}
        {success && <SuccessMessage theme={theme}>{success}</SuccessMessage>}

        <InfoSection theme={theme}>
          <InfoText theme={theme}>💡 1코인 = 100원</InfoText>
          <InfoText theme={theme}>개발 환경에서는 테스트 충전이 가능합니다.</InfoText>
        </InfoSection>
      </ModalContent>
    </ModalOverlay>
  );
};

export default PetCoinChargePage;

const ModalOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: ${props => props.theme?.colors?.overlay || 'rgba(0, 0, 0, 0.5)'};
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  padding: 20px;
`;

const ModalContent = styled.div`
  background: ${props => props.theme.colors.surface || '#ffffff'};
  border-radius: 16px;
  width: 100%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
`;

const ModalHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px;
  border-bottom: 1px solid ${props => props.theme.colors.border || '#e0e0e0'};
`;

const Title = styled.h2`
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: ${props => props.theme.colors.text || '#333'};
`;

const CloseButton = styled.button`
  background: none;
  border: none;
  font-size: 24px;
  color: ${props => props.theme.colors.textSecondary || '#666'};
  cursor: pointer;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s ease;

  &:hover {
    background: ${props => props.theme.colors.surfaceHover || '#f5f5f5'};
    color: ${props => props.theme.colors.text || '#333'};
  }
`;

const BalanceSection = styled.div`
  padding: 24px;
  background: ${props => props.theme.colors.surfaceElevated || '#f8f9fa'};
  border-bottom: 1px solid ${props => props.theme.colors.border || '#e0e0e0'};
`;

const BalanceLabel = styled.div`
  font-size: 14px;
  color: ${props => props.theme.colors.textSecondary || '#666'};
  margin-bottom: 8px;
`;

const BalanceAmount = styled.div`
  font-size: 32px;
  font-weight: 700;
  color: ${props => props.theme.colors.primary || '#FF7E36'};
`;

const ChargeSection = styled.div`
  padding: 24px;
`;

const SectionTitle = styled.h3`
  margin: 0 0 16px 0;
  font-size: 18px;
  font-weight: 600;
  color: ${props => props.theme.colors.text || '#333'};
`;

const ChargeOptions = styled.div`
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 24px;
`;

const ChargeOption = styled.div`
  padding: 20px;
  border: 2px solid ${props => 
    props.selected 
      ? (props.theme.colors.primary || '#FF7E36')
      : (props.theme.colors.border || '#e0e0e0')};
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: ${props => 
    props.selected 
      ? (props.theme.colors.primaryLight || '#fff5f0')
      : (props.theme.colors.surface || '#ffffff')};
  text-align: center;

  &:hover {
    border-color: ${props => props.theme.colors.primary || '#FF7E36'};
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }
`;

const CoinAmount = styled.div`
  font-size: 20px;
  font-weight: 600;
  color: ${props => props.theme.colors.text || '#333'};
  margin-bottom: 4px;
`;

const Price = styled.div`
  font-size: 14px;
  color: ${props => props.theme.colors.textSecondary || '#666'};
`;

const Divider = styled.div`
  display: flex;
  align-items: center;
  text-align: center;
  margin: 24px 0;
  color: ${props => props.theme.colors.textSecondary || '#666'};
  font-size: 14px;

  &::before,
  &::after {
    content: '';
    flex: 1;
    border-bottom: 1px solid ${props => props.theme.colors.border || '#e0e0e0'};
  }

  span {
    padding: 0 16px;
  }
`;

const CustomInputSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const CustomInputLabel = styled.label`
  font-size: 14px;
  font-weight: 500;
  color: ${props => props.theme.colors.text || '#333'};
`;

const CustomInputWrapper = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
`;

const CustomInput = styled.input`
  flex: 1;
  min-width: 150px;
  padding: 12px 16px;
  border: 2px solid ${props => props.theme.colors.border || '#e0e0e0'};
  border-radius: 8px;
  font-size: 16px;
  background: ${props => props.theme.colors.surface || '#ffffff'};
  color: ${props => props.theme.colors.text || '#333'};
  transition: all 0.2s ease;

  &:focus {
    outline: none;
    border-color: ${props => props.theme.colors.primary || '#FF7E36'};
  }

  &::placeholder {
    color: ${props => props.theme.colors.textLight || '#999'};
  }
`;

const CoinUnit = styled.span`
  font-size: 16px;
  color: ${props => props.theme.colors.text || '#333'};
  font-weight: 500;
`;

const PriceDisplay = styled.span`
  font-size: 14px;
  color: ${props => props.theme.colors.primary || '#FF7E36'};
  font-weight: 600;
`;

const CustomChargeButton = styled.button`
  padding: 12px 24px;
  background: ${props => props.theme.colors.primary || '#FF7E36'};
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    background: ${props => props.theme.colors.primaryDark || '#e66a2b'};
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(255, 126, 54, 0.3);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const SelectedOptionSection = styled.div`
  padding: 24px;
  background: ${props => props.theme.colors.surfaceElevated || '#f8f9fa'};
  border-top: 1px solid ${props => props.theme.colors.border || '#e0e0e0'};
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const SelectedInfo = styled.div`
  font-size: 16px;
  color: ${props => props.theme.colors.text || '#333'};
  text-align: center;
`;

const ChargeButton = styled.button`
  padding: 16px;
  background: ${props => props.theme.colors.primary || '#FF7E36'};
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 18px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    background: ${props => props.theme.colors.primaryDark || '#e66a2b'};
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(255, 126, 54, 0.3);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const ErrorMessage = styled.div`
  padding: 12px 24px;
  margin: 0 24px;
  background: #fee;
  color: #c33;
  border-radius: 8px;
  font-size: 14px;
  text-align: center;
`;

const SuccessMessage = styled.div`
  padding: 12px 24px;
  margin: 0 24px;
  background: #efe;
  color: #3c3;
  border-radius: 8px;
  font-size: 14px;
  text-align: center;
`;

const InfoSection = styled.div`
  padding: 16px 24px;
  background: ${props => props.theme.colors.surfaceElevated || '#f8f9fa'};
  border-top: 1px solid ${props => props.theme.colors.border || '#e0e0e0'};
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

const InfoText = styled.div`
  font-size: 12px;
  color: ${props => props.theme.colors.textSecondary || '#666'};
  text-align: center;
`;
