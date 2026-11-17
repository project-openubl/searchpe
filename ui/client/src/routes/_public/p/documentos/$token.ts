import { createFileRoute } from "@tanstack/react-router";

import { DocumentAccessRoute } from "@app/pages/public/document-access/DocumentAccessRoute";

// The buyer portal's capability link (DESIGN.md §12): the token is the only credential.
export const Route = createFileRoute("/_public/p/documentos/$token")({
  component: DocumentAccessRoute,
});
