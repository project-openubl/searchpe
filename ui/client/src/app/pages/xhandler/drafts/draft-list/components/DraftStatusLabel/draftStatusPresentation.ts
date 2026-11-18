import type { DraftStatus } from "@app/queries/xhandler/drafts";

/** How each draft status reads (ADR 0005). */
export const DRAFT_STATUS_PRESENTATION: Record<
  DraftStatus,
  { label: string; color: "blue" | "green" }
> = {
  draft: { label: "Borrador", color: "blue" },
  issued: { label: "Emitido", color: "green" },
};
