import axios from "axios";

import type {
  ISearchpeErrorData,
  IXhandlerErrorData,
} from "@app/queries/utils/openapiErrorTypes";

/** A failure as the UI shows it (toast or form alert), ADR 0006. */
export interface IMutationErrorMessage {
  /** What failed, written by the UI: "No se pudo …". */
  title: string;
  /** The server's `message` as returned, or a generic reason. */
  description?: string;
  /** The server's `detail`, shown under the message. */
  detail?: string;
}

export const PERMISSION_DENIED_DESCRIPTION =
  "No tienes permiso para realizar esta acción.";

export const NETWORK_ERROR_DESCRIPTION =
  "No se pudo conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.";

/** The generic reason for an HTTP error without a server message; never axios' English text. */
export const httpStatusDescription = (status: number): string =>
  `El servidor respondió con un error (HTTP ${status}).`;

const isXhandlerErrorData = (data: unknown): data is IXhandlerErrorData =>
  typeof data === "object" &&
  data !== null &&
  typeof (data as IXhandlerErrorData).kind === "string" &&
  typeof (data as IXhandlerErrorData).message === "string";

/** The server's own words for an error body of either backend, if it has any. */
export const describeApiError = (
  data: unknown,
): { message?: string; detail?: string } => {
  if (isXhandlerErrorData(data)) {
    return { message: data.message, detail: data.detail ?? undefined };
  }
  if (typeof data === "string") {
    return data.trim() && !data.trimStart().startsWith("<")
      ? { message: data.trim() }
      : {};
  }
  const message = (data as ISearchpeErrorData | null)?.message;
  return typeof message === "string" && message ? { message } : {};
};

/** The `kind` of an xhandler-service error (`invalid_document`, …), for the UI's logic only. */
export const getApiErrorKind = (error: unknown): string | undefined => {
  if (!axios.isAxiosError(error)) return undefined;
  const data: unknown = error.response?.data;
  return isXhandlerErrorData(data) ? data.kind : undefined;
};

/**
 * Turns a failed request into what the UI shows: `fallback` names what failed ("No se pudo
 * cancelar el trabajo"), the server's message and detail follow as returned, `kind` stays out.
 */
export const getMutationErrorMessage = ({
  error,
  fallback,
}: {
  error: unknown;
  fallback: string;
}): IMutationErrorMessage => {
  if (!axios.isAxiosError(error)) {
    return error instanceof Error && error.message
      ? { title: fallback, description: error.message }
      : { title: fallback };
  }
  if (!error.response) {
    return { title: fallback, description: NETWORK_ERROR_DESCRIPTION };
  }
  const { message, detail } = describeApiError(error.response.data);
  if (error.response.status === 403) {
    return {
      title: fallback,
      description: message ?? PERMISSION_DENIED_DESCRIPTION,
      detail,
    };
  }
  return {
    title: fallback,
    description: message ?? httpStatusDescription(error.response.status),
    detail,
  };
};
