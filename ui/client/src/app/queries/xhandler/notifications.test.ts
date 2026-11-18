import { describe, expect, it } from "vitest";

import { NOTIFICATION_KINDS, withDefaultPreferences } from "./notifications";

describe("withDefaultPreferences", () => {
  it("lists every known kind, on by default", () => {
    const preferences = withDefaultPreferences([]);
    expect(preferences.map((pref) => pref.kind)).toEqual([
      ...NOTIFICATION_KINDS,
    ]);
    expect(preferences.every((pref) => pref.email && pref.in_app)).toBe(true);
  });

  it("keeps stored rows, including kinds the UI doesn't know", () => {
    const preferences = withDefaultPreferences([
      { kind: "quota", email: false, in_app: true },
      { kind: "privacy.request", email: true, in_app: true },
    ]);
    expect(preferences.find((pref) => pref.kind === "quota")).toEqual({
      kind: "quota",
      email: false,
      in_app: true,
    });
    expect(preferences.at(-1)?.kind).toBe("privacy.request");
  });
});
