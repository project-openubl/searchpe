import { ApiClient, ClientFactory } from "api-client";

export const useClientInstance = (): ApiClient => {
  return ClientFactory.cluster("/api");
};

export default useClientInstance;
