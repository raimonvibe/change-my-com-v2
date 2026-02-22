// import axios from 'axios';
// import { API_URL } from '../env';

// export const api = axios.create({
//   baseURL: API_URL,
//   withCredentials: true,
// });

// api.interceptors.request.use((config) => {
//   const csrfToken = document.cookie
//     .split('; ')
//     .find(row => row.startsWith('XSRF-TOKEN='))
//     ?.split('=')[1];
  
//   if (csrfToken) {
//     config.headers['X-XSRF-TOKEN'] = decodeURIComponent(csrfToken);
//   }
  
//   return config;
// });


import axios from 'axios';
import { API_URL } from '../env';

export const api = axios.create({
  baseURL: API_URL,
  withCredentials: true,
});