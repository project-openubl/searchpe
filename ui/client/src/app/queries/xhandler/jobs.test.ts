import { describe, expect, it } from "vitest";

import { isJobCancelable, isJobState, isTerminalJobState } from "./jobs";

describe("job state helpers", () => {
  it("knows the terminal states, canceling excluded", () => {
    expect(isTerminalJobState("completed")).toBe(true);
    expect(isTerminalJobState("failed")).toBe(true);
    expect(isTerminalJobState("canceled")).toBe(true);
    expect(isTerminalJobState("canceling")).toBe(false);
    expect(isTerminalJobState("running")).toBe(false);
  });

  it("offers cancel for waiting and running jobs only", () => {
    expect(isJobCancelable("waiting")).toBe(true);
    expect(isJobCancelable("running")).toBe(true);
    expect(isJobCancelable("canceling")).toBe(false);
  });

  it("recognizes the API's states", () => {
    expect(isJobState("waiting")).toBe(true);
    expect(isJobState("skipped")).toBe(false);
  });
});
