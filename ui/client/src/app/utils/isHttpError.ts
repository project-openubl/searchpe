import axios from "axios";

export const isHttpError = ({
  error,
  status,
}: {
  error: unknown;
  status: number;
}): boolean => {
  return axios.isAxiosError(error) && error.response?.status === status;
};

export const isForbiddenError = (error: unknown): boolean => {
  return isHttpError({ error, status: 403 });
};

export const isNotFoundError = (error: unknown): boolean => {
  return isHttpError({ error, status: 404 });
};
