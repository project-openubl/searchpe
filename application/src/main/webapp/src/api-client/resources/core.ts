import { ClusterResource, IGroupVersionKindPlural } from "./common";

export class CoreClusterResource extends ClusterResource {
  private _gvk: IGroupVersionKindPlural;

  constructor(kind: CoreClusterResourceKind) {
    super();
    this._gvk = {
      group: "",
      version: "",
      kindPlural: kind,
    };
  }

  gvk(): IGroupVersionKindPlural {
    return this._gvk;
  }

  public listPath(): string {
    return [this.gvk().kindPlural].join("/");
  }
}

export class AdminClusterResource extends ClusterResource {
  private _gvk: IGroupVersionKindPlural;

  constructor(kind: AdminClusterResourceKind) {
    super();
    this._gvk = {
      group: "",
      version: "",
      kindPlural: kind,
    };
  }

  gvk(): IGroupVersionKindPlural {
    return this._gvk;
  }

  public listPath(): string {
    return ["/admin", this.gvk().kindPlural].join("/");
  }
}

export class CurrentUserClusterResource extends ClusterResource {
  private _gvk: IGroupVersionKindPlural;

  constructor(kind: CurrentUserClusterResourceKind) {
    super();
    this._gvk = {
      group: "",
      version: "",
      kindPlural: kind,
    };
  }

  gvk(): IGroupVersionKindPlural {
    return this._gvk;
  }

  public listPath(): string {
    return ["/current-user", this.gvk().kindPlural].join("/");
  }
}

export enum CoreClusterResourceKind {
  WhoAmI = "whoami",
  Version = "versions",
  Contribuyente = "contribuyentes",
}

export enum AdminClusterResourceKind {
  User = "users",
}

export enum CurrentUserClusterResourceKind {
  Credentials = "credentials",
  Profile = "profile",
}
