import { createFileRoute } from "@tanstack/react-router";

import { VersionList } from "@app/pages/searchpe/versions/version-list/VersionList";
import { VERSION_LIST_STATE } from "@app/pages/searchpe/versions/version-list/versionListState";
import { listSearch } from "@app/utils/listState";

const { validateSearch, search } = listSearch(VERSION_LIST_STATE);

export const Route = createFileRoute("/_authenticated/padron/versiones")({
  validateSearch,
  search,
  component: VersionList,
});
