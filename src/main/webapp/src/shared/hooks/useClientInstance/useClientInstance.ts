import { useKeycloak } from "@react-keycloak/web";
import { ClusterClient, ClientFactory } from "api-client";

export const useClientInstance = (): ClusterClient => {
  const { initialized, keycloak } = useKeycloak();

  let getToken: () => Promise<string | null>;
  if (initialized && keycloak.authenticated) {
    getToken = () =>
      new Promise<string>((resolve, reject) => {
        if (keycloak.token) {
          keycloak
            .updateToken(5)
            .then(() => resolve(keycloak.token!))
            .catch(() => reject("Failed to refresh token"));
        } else {
          keycloak.login();
          reject("Not logged in");
        }
      });
  } else {
    getToken = () => Promise.resolve(null);
  }

  const result = ClientFactory.cluster(getToken, "/api");
  return result;
};

export default useClientInstance;
