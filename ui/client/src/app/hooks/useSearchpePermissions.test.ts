import { describe, expect, it, vi } from "vitest";
import { renderHook } from "vitest-browser-react";

import { useWhoamiQuery } from "@app/queries/searchpe/whoami";

import {
  hasSearchpePermission,
  useSearchpePermissions,
} from "./useSearchpePermissions";

vi.mock(import("@app/queries/searchpe/whoami"), () => ({
  useWhoamiQuery: vi.fn(),
}));

const mockWhoami = (state: { isSuccess: boolean; permissions?: string[] }) =>
  vi.mocked(useWhoamiQuery).mockReturnValue({
    isSuccess: state.isSuccess,
    data: state.permissions
      ? { username: "alice", password: "", permissions: state.permissions }
      : undefined,
  } as unknown as ReturnType<typeof useWhoamiQuery>);

describe("hasSearchpePermission", () => {
  it("grants a listed permission, and everything to admin:app", () => {
    expect(
      hasSearchpePermission({ permissions: ["search"], permission: "search" }),
    ).toBe(true);
    expect(
      hasSearchpePermission({
        permissions: ["search"],
        permission: "version:write",
      }),
    ).toBe(false);
    expect(
      hasSearchpePermission({
        permissions: ["admin:app"],
        permission: "version:write",
      }),
    ).toBe(true);
  });
});

describe("useSearchpePermissions", () => {
  it("allows everything until whoami answers", async () => {
    mockWhoami({ isSuccess: false });
    const { result } = await renderHook(() => useSearchpePermissions());

    expect(result.current.can("version:write")).toBe(true);
    expect(result.current.disabledProps("version:write")).toEqual({});
  });

  it("allows everything when searchpe runs without authorization", async () => {
    vi.mocked(useWhoamiQuery).mockReturnValue({
      isSuccess: true,
      data: { username: "", password: "", permissions: [] },
    } as unknown as ReturnType<typeof useWhoamiQuery>);
    const { result } = await renderHook(() => useSearchpePermissions());

    expect(result.current.can("version:write")).toBe(true);
  });

  it("disables an action the user lacks, with the reason", async () => {
    mockWhoami({ isSuccess: true, permissions: ["search"] });
    const { result } = await renderHook(() => useSearchpePermissions());

    expect(result.current.can("search")).toBe(true);
    expect(result.current.can("version:write")).toBe(false);
    expect(result.current.disabledProps("version:write")).toEqual({
      isAriaDisabled: true,
      tooltipProps: {
        content: "Necesitas el permiso version:write en searchpe.",
      },
    });
  });
});
