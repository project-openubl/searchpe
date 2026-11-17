import { use } from "react";

import { AuthContext } from "@app/context/Auth/AuthContext";

export const useAuth = () => {
  const context = use(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
};
