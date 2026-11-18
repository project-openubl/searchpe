import { createFileRoute } from "@tanstack/react-router";

import { DetailRouteError } from "@app/components/DetailRouteError";
import { DetailRoutePending } from "@app/components/DetailRoutePending";
import { DraftEditRoute } from "@app/pages/xhandler/drafts/draft-edit/DraftEditRoute";
import { companyDetailQueryOptions } from "@app/queries/xhandler/companies";
import { draftDetailQueryOptions } from "@app/queries/xhandler/drafts";

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/borradores/$draftId",
)({
  loader: async ({
    context: { queryClient },
    params: { tenantId, draftId },
  }) => {
    const draft = await queryClient.query({
      ...draftDetailQueryOptions({ tenantId, draftId }),
      staleTime: "static",
    });
    // The page shows the draft's company (name, environment, series).
    await queryClient.query({
      ...companyDetailQueryOptions({
        tenantId,
        companyId: draft.company_id ?? "",
      }),
      staleTime: "static",
    });
    return { draftId };
  },
  pendingComponent: () => <DetailRoutePending label="Cargando borrador" />,
  errorComponent: DetailRouteError,
  component: DraftEditRoute,
});
