import { ResponseType } from "axios";
import { ApiClient } from "./client";

export class ClientFactoryUnknownClusterError extends Error {
  constructor(clusterName: string) {
    super(`Unknown cluster requested: ${clusterName}`);
    Object.setPrototypeOf(this, ClientFactoryUnknownClusterError.prototype);
  }
}

export class ClientFactoryMissingUserError extends Error {
  constructor() {
    super("Current user missing from client factory");
    Object.setPrototypeOf(this, ClientFactoryMissingUserError.prototype);
  }
}

export class ClientFactoryMissingApiRoot extends Error {
  constructor() {
    super("Cluster API URL missing from client factory");
    Object.setPrototypeOf(this, ClientFactoryMissingUserError.prototype);
  }
}

export const ClientFactory = {
  cluster: (
    clusterApi: string,
    customResponseType: ResponseType = "json"
  ): ApiClient => {
    if (!clusterApi) {
      throw new ClientFactoryMissingApiRoot();
    }

    const newClient = new ApiClient(clusterApi, customResponseType);
    return newClient;
  },
};
