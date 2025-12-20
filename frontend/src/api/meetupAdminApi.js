import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/admin/meetups';

const getToken = () => localStorage.getItem('accessToken') || localStorage.getItem('token');

const api = axios.create({
    baseURL: BASE_URL,
    headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
    const token = getToken();
    if (token && !config.headers.Authorization) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

export const meetupAdminApi = {
    listMeetups: (params) => api.get('', { params }),
    getMeetup: (id) => api.get(`/${id}`),
    deleteMeetup: (id) => api.delete(`/${id}`),
    getParticipants: (id) => api.get(`/${id}/participants`),
};

