import { createFileRoute } from "@tanstack/react-router";

// Every page under this layout needs a session; public pages live under `_public` (ADR 0003).
export const Route = createFileRoute("/_authenticated")({
  beforeLoad: ({ context, location }) => {
    const { auth } = context;

    if (!auth || auth.isAuthenticated()) {
      return;
    }
    if (auth.error) {
      throw auth.error;
    }

    auth.login(location.href);
  },
});
