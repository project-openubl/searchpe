import { createFileRoute } from "@tanstack/react-router";

import { MemberList } from "@app/pages/xhandler/members/member-list/MemberList";

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/miembros",
)({
  component: MemberList,
});
