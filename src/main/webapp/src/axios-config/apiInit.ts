import axios from "axios";

export const initApi = (base: string) => {
  axios.defaults.baseURL = base;
};

export const initInterceptors = (getToken: () => Promise<string>) => {
  axios.interceptors.request.use(
    async (config) => {
      const token = await getToken();
      if (token) {
        config.headers && (config.headers["Authorization"] = "Bearer " + token);
      }
      return config;
    },
    (error) => {
      Promise.reject(error);
    }
  );
};
