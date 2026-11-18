import { createFileRoute } from "@tanstack/react-router";

import { DraftList } from "@app/pages/xhandler/drafts/draft-list/DraftList";
import { DRAFT_LIST_STATE } from "@app/pages/xhandler/drafts/draft-list/draftListState";
import { listSearch } from "@app/utils/listState";

const { validateSearch, search } = listSearch(DRAFT_LIST_STATE);

// Manual issuing (DESIGN.md §11.10): the tenant's drafts.
export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/borradores/",
)({
  validateSearch,
  search,
  component: DraftList,
});
