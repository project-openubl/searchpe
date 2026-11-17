import { describe, expect, it } from "vitest";

import {
  isVersionCancellable,
  isVersionDeletable,
  isVersionFinished,
} from "./versions";

describe("version status helpers", () => {
  it("treats completed, failed and cancelled as finished", () => {
    expect(isVersionFinished("COMPLETED")).toBe(true);
    expect(isVersionFinished("ERROR")).toBe(true);
    expect(isVersionFinished("CANCELLED")).toBe(true);
    expect(isVersionFinished("IMPORTING")).toBe(false);
    expect(isVersionFinished(undefined)).toBe(false);
  });

  it("cancels only an import still in progress", () => {
    expect(isVersionCancellable("SCHEDULED")).toBe(true);
    expect(isVersionCancellable("DOWNLOADING")).toBe(true);
    expect(isVersionCancellable("CANCELLING")).toBe(false);
    expect(isVersionCancellable("DELETING")).toBe(false);
    expect(isVersionCancellable("COMPLETED")).toBe(false);
  });

  it("deletes only finished versions", () => {
    expect(isVersionDeletable("COMPLETED")).toBe(true);
    expect(isVersionDeletable("IMPORTING")).toBe(false);
  });
});
