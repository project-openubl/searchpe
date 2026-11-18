import { AxiosError, type AxiosResponse } from "axios";
import { describe, expect, it } from "vitest";

import { fileNameFromDisposition, withJsonErrorBody } from "./fileDownload";

describe("fileNameFromDisposition", () => {
  it("reads the server's file name", () => {
    expect(
      fileNameFromDisposition({
        header: 'attachment; filename="20123456789-01-F001-1.xml"',
        fallback: "x",
      }),
    ).toBe("20123456789-01-F001-1.xml");
  });

  it("falls back without a usable header", () => {
    expect(
      fileNameFromDisposition({ header: undefined, fallback: "a.pdf" }),
    ).toBe("a.pdf");
    expect(
      fileNameFromDisposition({ header: "inline", fallback: "a.pdf" }),
    ).toBe("a.pdf");
  });
});

describe("withJsonErrorBody", () => {
  it("turns a blob error body back into the API's error", async () => {
    const body = { kind: "not_found", message: "No existe." };
    const error = new AxiosError("Request failed", "ERR_BAD_REQUEST");
    error.response = {
      status: 404,
      data: new Blob([JSON.stringify(body)], { type: "application/json" }),
    } as AxiosResponse;

    const result = (await withJsonErrorBody(error)) as AxiosError;

    expect(result.response?.data).toEqual(body);
  });
});
