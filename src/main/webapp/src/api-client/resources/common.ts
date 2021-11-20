export type ApiResource = ClusterResource;

export interface IResource {
  listPath(): string;
  idPath(name: string): string;
}

export interface IKindPlural {
  kindPlural: string;
}

function idPath(listPath: string, name: string) {
  return [listPath, name].join("/");
}

export abstract class ClusterResource implements ApiResource {
  public abstract gvk(): IKindPlural;

  public listPath(): string {
    return ["/", this.gvk().kindPlural].join("/");
  }

  public idPath(name: string): string {
    return idPath(this.listPath(), name);
  }
}
