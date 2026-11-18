import { createFileRoute } from "@tanstack/react-router";

import { DocumentList } from "@app/pages/xhandler/documents/document-list/DocumentList";
import { DOCUMENT_LIST_STATE } from "@app/pages/xhandler/documents/document-list/documentListState";
import { listSearch } from "@app/utils/listState";

const { validateSearch, search } = listSearch(DOCUMENT_LIST_STATE);

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/documentos/",
)({
  validateSearch,
  search,
  component: DocumentList,
});
