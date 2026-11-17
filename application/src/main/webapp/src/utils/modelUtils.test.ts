import { AxiosError, AxiosHeaders, InternalAxiosRequestConfig } from "axios";
import {
  getAxiosErrorMessage,
  isVersionCancellable,
  isVersionFinished,
} from "./modelUtils";

describe("modelUtils", () => {
  const config: InternalAxiosRequestConfig = { headers: new AxiosHeaders() };

  it("getAxiosErrorMessage should pick AxiosError message", () => {
    const errorMsg = "Network error";

    const mockAxiosError = new AxiosError(errorMsg, undefined, config);

    const errorMessage = getAxiosErrorMessage(mockAxiosError);
    expect(errorMessage).toBe(errorMsg);
  });

  it("getAxiosErrorMessage should pick AxiosError body message", () => {
    const errorMsg = "Internal server error";

    const mockAxiosError = new AxiosError(
      "Network error",
      undefined,
      config,
      undefined,
      {
        data: {
          message: errorMsg,
        },
        status: 400,
        statusText: "",
        headers: {},
        config,
      }
    );

    const errorMessage = getAxiosErrorMessage(mockAxiosError);
    expect(errorMessage).toBe(errorMsg);
  });

  it.each([
    ["SCHEDULED", false, true],
    ["DOWNLOADING", false, true],
    ["UNZIPPING", false, true],
    ["IMPORTING", false, true],
    ["INDEXING", false, true],
    ["DELETING", false, true],
    ["CANCELLING", false, false],
    ["COMPLETED", true, false],
    ["ERROR", true, false],
    ["CANCELLED", true, false],
  ] as const)(
    "status %s: finished=%s, cancellable=%s",
    (status, finished, cancellable) => {
      expect(isVersionFinished(status)).toBe(finished);
      expect(isVersionCancellable(status)).toBe(cancellable);
    }
  );
});
