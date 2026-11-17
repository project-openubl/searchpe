/**
 * Which backends the stack under test runs: searchpe is optional (ADR 0001), so its specs skip
 * with a reason when `E2E_SEARCHPE` is `false`.
 */
export const SEARCHPE_ENABLED = process.env.E2E_SEARCHPE !== "false";
