import { AxiosError } from "axios";

import { VersionStatus } from "api/models";

export const getBaseApiUrl = (): string => {
  return (window as any)["SEARCHPE_API_URL"] || "/api";
};

// Axios error

export const getAxiosErrorMessage = (axiosError: AxiosError) => {
  const data = axiosError.response?.data as { message?: string } | undefined;
  if (data?.message) {
    return data.message;
  }
  return axiosError.message;
};

export const formatNumber = (value: number, fractionDigits = 2) => {
  return value.toLocaleString("en", {
    style: "decimal",
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  });
};

// Form

export const getValidatedFromError = (
  error: any
): "success" | "warning" | "error" | "default" => {
  return error ? "error" : "default";
};

export const getValidatedFromErrorTouched = (
  error: any,
  touched: boolean | undefined
): "success" | "warning" | "error" | "default" => {
  return error && touched ? "error" : "default";
};

// Version

/**
 * Whether each status is final, so no job is working on the version anymore. Mirrors Status#isFinished in the backend;
 * being a Record, adding a status fails to compile until it is classified here.
 */
const FINISHED_VERSION_STATUSES: Record<VersionStatus, boolean> = {
  SCHEDULED: false,
  DOWNLOADING: false,
  UNZIPPING: false,
  IMPORTING: false,
  INDEXING: false,
  DELETING: false,
  CANCELLING: false,
  COMPLETED: true,
  ERROR: true,
  CANCELLED: true,
};

export const isVersionFinished = (status: VersionStatus): boolean =>
  FINISHED_VERSION_STATUSES[status];

export const isVersionCancellable = (status: VersionStatus): boolean =>
  !isVersionFinished(status) && status !== "CANCELLING";
