import { createFileRoute } from "@tanstack/react-router";

import { PartnerList } from "@app/pages/xhandler/partners/partner-list/PartnerList";

export const Route = createFileRoute("/_authenticated/socios/")({
  component: PartnerList,
});
