import { API_ROOT, createAuthAxios } from './apiClient';

const api = createAuthAxios(API_ROOT);

export const paymentApi = {
  getBalance: async () => {
    const response = await api.get('/payment/balance');
    return response.data;
  },

  getTransactions: async (page = 0, size = 20) => {
    const response = await api.get('/payment/transactions', {
      params: { page, size },
    });
    return response.data;
  },

  getTransactionDetail: async (transactionId) => {
    const response = await api.get(`/payment/transactions/${transactionId}`);
    return response.data;
  },

  chargeCoins: async (amount, description) => {
    const response = await api.post('/payment/charge', { amount, description });
    return response.data;
  },
};
