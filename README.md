![CI](https://github.com/project-openubl/xhandler-service/workflows/CI/badge.svg)
[![License](https://img.shields.io/badge/Apache-2.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)

[![Project Chat](https://img.shields.io/badge/zulip-join_chat-brightgreen.svg?style=for-the-badge&logo=zulip)](https://projectopenubl.zulipchat.com/)

# xhandler platform

Todos los servicios de xhandler, construidos y desplegados juntos:

- [Searchpe](./searchpe): microservicio que expone los datos del `padrón reducido` de la SUNAT.
- [xhandler-service](./xhandler): servicio multi-tenant de comprobantes electrónicos SUNAT
  (diseño: [xhandler/docs/DESIGN.md](./xhandler/docs/DESIGN.md)).
- [UI](./ui): la interfaz única de todos los servicios.
- [Operator](./operator): operador de Kubernetes para todos los servicios.

## Links

- [Documentación](https://project-openubl.github.io)
- [Discusiones](https://github.com/project-openubl/xhandler-service/discussions)

## Releasing

Releases are made with the
[Release searchpe](https://github.com/project-openubl/release-tools#releasing-searchpe) workflow of
release-tools. It tags this repository and publishes the GitHub release, which runs
[release.yml](./.github/workflows/release.yml): the images and the release assets. Then
release-tools opens the OperatorHub pull requests. Don't create releases by hand: release-tools checks
CI first.

## License

- [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
