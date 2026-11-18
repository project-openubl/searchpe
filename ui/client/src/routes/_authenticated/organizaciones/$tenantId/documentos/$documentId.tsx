import { createFileRoute } from "@tanstack/react-router";

import { DetailRouteError } from "@app/components/DetailRouteError";
import { DetailRoutePending } from "@app/components/DetailRoutePending";
import { DocumentDetailRoute } from "@app/pages/xhandler/documents/document-detail/DocumentDetailRoute";
import { documentDetailQueryOptions } from "@app/queries/xhandler/documents";

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/documentos/$documentId",
)({
  loader: async ({
    context: { queryClient },
    params: { tenantId, documentId },
  }) => {
    await queryClient.query({
      ...documentDetailQueryOptions({ tenantId, documentId }),
      staleTime: "static",
    });
    return { documentId };
  },
  pendingComponent: () => <DetailRoutePending label="Cargando comprobante" />,
  errorComponent: DetailRouteError,
  component: DocumentDetailRoute,
});
