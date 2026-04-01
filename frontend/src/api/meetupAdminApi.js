import { createAuthAxios } from './apiClient';

const api = createAuthAxios('http://localhost:8080/api/admin/meetups');

export const meetupAdminApi = {
    listMeetups: (params) => api.get('', { params }),
    getMeetup: (id) => api.get(`/${id}`),
    deleteMeetup: (id) => api.delete(`/${id}`),
    getParticipants: (id) => api.get(`/${id}/participants`),
};

