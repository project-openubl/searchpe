import { AxiosError } from "axios";
import { describe, expect, it } from "vitest";

import { isForbiddenError, isHttpError, isNotFoundError } from "./isHttpError";

const axiosErrorWithStatus = (status: number): AxiosError => {
  const error = new AxiosError("request failed");
  error.response = {
    status,
    statusText: "Error",
    headers: {},
    config: { headers: {} } as never,
    data: {},
  };
  return error;
};

describe("isHttpError helpers", () => {
  it("detects matching axios status codes", () => {
    expect(isHttpError({ error: axiosErrorWithStatus(403), status: 403 })).toBe(
      true,
    );
    expect(isHttpError({ error: axiosErrorWithStatus(404), status: 403 })).toBe(
      false,
    );
    expect(isHttpError({ error: new Error("nope"), status: 403 })).toBe(false);
  });

  it("detects forbidden and not-found errors", () => {
    expect(isForbiddenError(axiosErrorWithStatus(403))).toBe(true);
    expect(isForbiddenError(axiosErrorWithStatus(401))).toBe(false);
    expect(isNotFoundError(axiosErrorWithStatus(404))).toBe(true);
    expect(isNotFoundError(axiosErrorWithStatus(500))).toBe(false);
  });
});
