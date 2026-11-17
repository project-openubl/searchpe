---
paths:
  - client/src/app/**
---
# File placement (`client/src/app/`)

## React directories

Place each module at the narrowest scope that contains every file using it (tests don't
count):

| Used by            | Scope            | Location                                                      |
| :----------------- | :--------------- | :------------------------------------------------------------ |
| One component      | Component folder | Next to the component, in its folder                          |
| One page           | Page directory   | The page's `components/`, `hooks/`, `utils/` or `context/`    |
| More than one page | App root         | `@app/components`, `@app/hooks`, `@app/utils`, `@app/context` |

Zustand stores are always app-wide and live in `@app/stores` ([stores](stores.md)); query
modules in `@app/queries/<backend>/` ([queries](queries.md)).

Only its users decide a module's scope, never its kind. Re-check the scope whenever a module or
its users move: a module that gains a user outside its scope moves up; one whose users all moved
into a narrower scope moves with them.

**Component folder.** A component with private modules gets a flat folder `X/` holding `X.tsx`,
those modules, their tests and `index.ts`. No `components/`, `hooks/` or `context/` inside it.

**Page directory.** Its root holds the page's main component and, if it has one, its
route-to-component adapter (`*DetailRoute.tsx`).

**Areas.** Pages group by product, then entity: `pages/xhandler/<entity>/<page-dir>/`,
`pages/searchpe/<entity>/<page-dir>/`; cross-product pages sit in `pages/account/`,
`pages/home/`, `pages/public/` (public routes only) and `pages/not-found/`. Area and entity
directories hold only page directories, never `components/`, `hooks/`, `utils/` or `context/`.
A module shared by pages of one area goes to the app root.

```
client/src/app/
  components/  hooks/  context/  utils/  stores/  queries/{xhandler,searchpe,utils}/
  pages/
    searchpe/                             # area: page directories only
      versions/                           # entity: page directories only
        version-list/                     # page directory
          VersionList.tsx  versionListState.ts
          components/
            VersionStatusLabel/           # component folder
              VersionStatusLabel.tsx  VersionStatusLabel.test.tsx  index.ts
    xhandler/
      companies/
        company-list/CompanyList.tsx
        company-detail/CompanyDetail.tsx  CompanyDetailRoute.tsx
```

## React Context

A context follows the same scopes as any module. It is a pair: `<Name>Context.ts` holds the
`createContext` call and value type, `<Name>Provider.tsx` holds the provider. Never both in
one file. App root or page directory: the pair gets its own folder, `context/<Name>/`.

## Checklist

Pass/fail rubric for `/eval`.

- [ ] Each new or moved module, and each module whose users moved, sits at the narrowest
      scope that contains all its users: component folder → page directory → app root.
- [ ] Component folders are flat and hold an `index.ts`.
- [ ] A page directory's root holds its main component and its route adapter, if any.
- [ ] Pages sit under their product area (`xhandler/`, `searchpe/`) or a cross-product area;
      no `components/`, `hooks/`, `utils/` or `context/` under an area or entity directory.
- [ ] New contexts sit at the narrowest scope, split into `<Name>Context.ts` +
      `<Name>Provider.tsx`.
