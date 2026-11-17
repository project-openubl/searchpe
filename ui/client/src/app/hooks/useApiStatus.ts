import { use } from "react";

import { ApiStatusContext } from "@app/context/ApiStatus/ApiStatusContext";

export const useApiStatus = () => {
  const context = use(ApiStatusContext);
  if (!context) {
    throw new Error("useApiStatus must be used within an ApiStatusProvider");
  }
  return context;
};
