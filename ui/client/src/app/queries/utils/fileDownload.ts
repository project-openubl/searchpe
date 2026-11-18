import axios, { type AxiosResponse } from "axios";

/** The file name of a `Content-Disposition: attachment; filename="…"` header, else `fallback`. */
export const fileNameFromDisposition = ({
  header,
  fallback,
}: {
  header: unknown;
  fallback: string;
}): string => {
  if (typeof header !== "string") return fallback;
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(header);
  if (!match) return fallback;
  try {
    return decodeURIComponent(match[1]);
  } catch {
    return match[1];
  }
};

/** Hands a file to the browser's save flow. */
export const saveBlob = ({
  blob,
  fileName,
}: {
  blob: Blob;
  fileName: string;
}): void => {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  link.click();
  // Revoked after the click has been handled, or some browsers cancel the download.
  setTimeout(() => URL.revokeObjectURL(url), 0);
};

/**
 * A failed `responseType: "blob"` request carries its `{ kind, message, detail }` body as a Blob:
 * read it back so `getMutationErrorMessage` shows the server's own words (ADR 0006).
 */
export const withJsonErrorBody = async (error: unknown): Promise<unknown> => {
  if (axios.isAxiosError(error) && error.response?.data instanceof Blob) {
    const text = await error.response.data.text();
    try {
      error.response.data = JSON.parse(text) as unknown;
    } catch {
      error.response.data = text;
    }
  }
  return error;
};

/**
 * Runs a binary download through the generated client and saves it under the server's file
 * name. The request must ask for `responseType: "blob"`.
 */
export const downloadFile = async ({
  request,
  fallbackName,
}: {
  request: () => Promise<AxiosResponse<unknown>>;
  fallbackName: string;
}): Promise<void> => {
  let response: AxiosResponse<unknown>;
  try {
    response = await request();
  } catch (error) {
    throw await withJsonErrorBody(error);
  }
  const blob =
    response.data instanceof Blob
      ? response.data
      : new Blob([
          typeof response.data === "string"
            ? response.data
            : JSON.stringify(response.data),
        ]);
  saveBlob({
    blob,
    fileName: fileNameFromDisposition({
      header: response.headers["content-disposition"],
      fallback: fallbackName,
    }),
  });
};
