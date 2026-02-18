import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

// 토큰을 헤더에 추가하는 함수
const getAuthHeaders = () => {
  const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
  return {
    'Content-Type': 'application/json',
    ...(token && { Authorization: `Bearer ${token}` }),
  };
};

export const paymentApi = {
  // 코인 잔액 조회
  getBalance: async () => {
    const response = await axios.get(`${API_BASE_URL}/payment/balance`, {
      headers: getAuthHeaders(),
    });
    return response.data;
  },

  // 거래 내역 조회 (Page 응답: content, totalElements, totalPages 등)
  getTransactions: async (page = 0, size = 20) => {
    const response = await axios.get(`${API_BASE_URL}/payment/transactions`, {
      params: { page, size },
      headers: getAuthHeaders(),
    });
    return response.data; // { content: [...], totalElements, totalPages, ... }
  },

  // 코인 충전 (테스트용 - 개발 환경에서만)
  chargeCoins: async (amount, description) => {
    const response = await axios.post(
      `${API_BASE_URL}/payment/charge`,
      { amount, description },
      { headers: getAuthHeaders() }
    );
    return response.data;
  },
};
