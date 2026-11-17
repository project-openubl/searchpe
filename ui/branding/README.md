# Branding

Names, logo and links baked into the UI at build time. Choose another folder with
`BRANDING=<dir>` (relative to `ui/`) when building `common`.

- `strings.json`: titles and masthead/about settings (`<%= brandingRoot %>` points at this folder
  once copied into the build).
- `manifest.json`, `favicon.ico`: the web app manifest and icon.
- `images/openubl-logo.svg`: a placeholder logo; replace it with the official artwork.
