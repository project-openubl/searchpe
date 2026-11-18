import type React from "react";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import { renderHook } from "vitest-browser-react";

import { issueDraft, updateDraft } from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import {
  draftKeys,
  getDraftConflictKind,
  useDraftIssueMutation,
  useDraftUpdateMutation,
} from "./drafts";

vi.mock(import("@app/client/xhandler"), () => ({
  createDraft: vi.fn(),
  deleteDraft: vi.fn(),
  getDraft: vi.fn(),
  issueDraft: vi.fn(),
  listDrafts: vi.fn(),
  updateDraft: vi.fn(),
}));
vi.mock(import("@app/hooks/useSelectedTenantId"), () => ({
  useSelectedTenantId: vi.fn(),
}));
vi.mock(import("./documents"), () => ({
  documentKeys: {
    list: (tenantId: string) => ["documents", tenantId, "list"],
  } as never,
}));

const INPUT = { kind: "Invoice", spec: {}, version: 4 };

const conflict = (kind: string, status = 409) => ({
  isAxiosError: true,
  response: { status, data: { kind, message: "Conflicto" } },
});

const setUp = () => {
  vi.mocked(useSelectedTenantId).mockReturnValue("t1");
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  });
  const invalidate = vi.spyOn(queryClient, "invalidateQueries");
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return { invalidate, wrapper };
};

const invalidatedKeys = (invalidate: {
  mock: { calls: unknown[][] };
}): unknown[] =>
  invalidate.mock.calls.map(
    ([filters]) => (filters as { queryKey: unknown }).queryKey,
  );

describe("getDraftConflictKind", () => {
  it("reads the 409 kinds of a draft write only", () => {
    expect(getDraftConflictKind(conflict("draft_issued"))).toBe("draft_issued");
    expect(getDraftConflictKind(conflict("draft_changed"))).toBe(
      "draft_changed",
    );
    expect(getDraftConflictKind(conflict("other_conflict"))).toBeUndefined();
    expect(
      getDraftConflictKind(conflict("draft_changed", 422)),
    ).toBeUndefined();
    expect(getDraftConflictKind(new Error("x"))).toBeUndefined();
  });
});

describe("draft writes after a 409", () => {
  it("refetch the draft and the lists when a save conflicts", async () => {
    const { invalidate, wrapper } = setUp();
    vi.mocked(updateDraft).mockRejectedValue(conflict("draft_changed"));
    const onError = vi.fn();
    const { result } = await renderHook(
      () => useDraftUpdateMutation("d1", { onError }),
      { wrapper },
    );

    await expect(result.current.mutateAsync(INPUT)).rejects.toEqual(
      conflict("draft_changed"),
    );

    expect(invalidatedKeys(invalidate)).toEqual(
      expect.arrayContaining([
        draftKeys.detail("t1", "d1"),
        draftKeys.list("t1"),
      ]),
    );
    expect(onError).toHaveBeenCalledOnce();
  });

  it("refetch the draft and the lists when an issue conflicts", async () => {
    const { invalidate, wrapper } = setUp();
    vi.mocked(issueDraft).mockRejectedValue(conflict("draft_issued"));
    const { result } = await renderHook(() => useDraftIssueMutation(), {
      wrapper,
    });

    await expect(
      result.current.mutateAsync({
        draftId: "d1",
        version: 4,
        idempotencyKey: "k",
      }),
    ).rejects.toEqual(conflict("draft_issued"));

    expect(invalidatedKeys(invalidate)).toEqual(
      expect.arrayContaining([
        draftKeys.detail("t1", "d1"),
        draftKeys.list("t1"),
      ]),
    );
  });

  it("leave the cache alone on any other failure", async () => {
    const { invalidate, wrapper } = setUp();
    vi.mocked(updateDraft).mockRejectedValue(conflict("invalid_document", 422));
    const { result } = await renderHook(() => useDraftUpdateMutation("d1"), {
      wrapper,
    });

    await expect(result.current.mutateAsync(INPUT)).rejects.toEqual(
      conflict("invalid_document", 422),
    );

    expect(invalidate).not.toHaveBeenCalled();
  });
});
