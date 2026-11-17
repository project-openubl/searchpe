import { createFileRoute } from "@tanstack/react-router";

import { Profile } from "@app/pages/account/profile/Profile";

export const Route = createFileRoute("/_authenticated/perfil")({
  component: Profile,
});
