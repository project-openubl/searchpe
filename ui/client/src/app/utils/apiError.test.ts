import {
  AxiosError,
  AxiosHeaders,
  type InternalAxiosRequestConfig,
} from "axios";
import { describe, expect, it } from "vitest";

import {
  getApiErrorKind,
  getMutationErrorMessage,
  httpStatusDescription,
  NETWORK_ERROR_DESCRIPTION,
  PERMISSION_DENIED_DESCRIPTION,
} from "./apiError";

const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig;

const httpError = ({ status, data }: { status: number; data: unknown }) =>
  new AxiosError("Request failed", "ERR_BAD_REQUEST", config, undefined, {
    status,
    statusText: "",
    data,
    headers: {},
    config,
  });

describe("getMutationErrorMessage", () => {
  it("shows xhandler's message and detail, never its kind", () => {
    const error = httpError({
      status: 422,
      data: {
        kind: "invalid_document",
        message: "El comprobante no es válido.",
        detail: "SUNAT 2335",
      },
    });

    expect(
      getMutationErrorMessage({ error, fallback: "No se pudo emitir" }),
    ).toEqual({
      title: "No se pudo emitir",
      description: "El comprobante no es válido.",
      detail: "SUNAT 2335",
    });
    expect(getApiErrorKind(error)).toBe("invalid_document");
  });

  it("shows searchpe's message", () => {
    const error = httpError({
      status: 400,
      data: { message: "Advanced search has been disabled" },
    });

    expect(
      getMutationErrorMessage({ error, fallback: "No se pudo buscar" }),
    ).toEqual({
      title: "No se pudo buscar",
      description: "Advanced search has been disabled",
      detail: undefined,
    });
  });

  it("explains a 403 without a server message", () => {
    const error = httpError({ status: 403, data: "" });

    expect(
      getMutationErrorMessage({ error, fallback: "No se pudo cancelar" })
        .description,
    ).toBe(PERMISSION_DENIED_DESCRIPTION);
  });

  it("ignores an HTML error page", () => {
    const error = httpError({ status: 502, data: "<html>Bad gateway</html>" });

    expect(
      getMutationErrorMessage({ error, fallback: "No se pudo crear" })
        .description,
    ).toBe("El servidor respondió con un error (HTTP 502).");
  });

  it("names the status in Spanish when the server says nothing", () => {
    const error = httpError({ status: 500, data: undefined });

    expect(
      getMutationErrorMessage({ error, fallback: "No se pudo crear" })
        .description,
    ).toBe(httpStatusDescription(500));
    expect(httpStatusDescription(500)).toContain("500");
  });

  it("says the server couldn't be reached when there is no response", () => {
    const error = new AxiosError("Network Error", "ERR_NETWORK", config);

    expect(
      getMutationErrorMessage({ error, fallback: "No se pudo crear" }),
    ).toEqual({
      title: "No se pudo crear",
      description: NETWORK_ERROR_DESCRIPTION,
    });
  });

  it("uses the message of a non-HTTP error", () => {
    expect(
      getMutationErrorMessage({
        error: new Error("boom"),
        fallback: "No se pudo crear",
      }),
    ).toEqual({ title: "No se pudo crear", description: "boom" });
  });
});
