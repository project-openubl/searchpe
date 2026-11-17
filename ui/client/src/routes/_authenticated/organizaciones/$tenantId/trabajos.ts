import { createFileRoute } from "@tanstack/react-router";

import { JobList } from "@app/pages/xhandler/jobs/job-list/JobList";
import { JOB_LIST_STATE } from "@app/pages/xhandler/jobs/job-list/jobListState";
import { listSearch } from "@app/utils/listState";

const { validateSearch, search } = listSearch(JOB_LIST_STATE);

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/trabajos",
)({
  validateSearch,
  search,
  component: JobList,
});
