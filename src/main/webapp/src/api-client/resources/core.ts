import { ClusterResource, IKindPlural } from "./common";

export enum CoreClusterResourceKind {
  Version = "versions",
  Contribuyente = "contribuyentes",
  LocalUser = "users",
}

export class CoreClusterResource extends ClusterResource {
  private _gvk: IKindPlural;

  constructor(kind: CoreClusterResourceKind) {
    super();

    this._gvk = {
      kindPlural: kind,
    };
  }

  gvk(): IKindPlural {
    return this._gvk;
  }

  public listPath(): string {
    return ["/", this.gvk().kindPlural].join("/");
  }
}

export class AdminClusterResource extends ClusterResource {
  private _gvk: IKindPlural;

  constructor(kind: CoreClusterResourceKind) {
    super();

    this._gvk = {
      kindPlural: kind,
    };
  }

  gvk(): IKindPlural {
    return this._gvk;
  }

  public listPath(): string {
    return ["/admin", this.gvk().kindPlural].join("/");
  }
}
