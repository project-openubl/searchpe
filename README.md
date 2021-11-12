![CI](https://github.com/project-openubl/searchpe/workflows/CI/badge.svg)
[![License](https://img.shields.io/badge/License-EPL%202.0-green.svg)](https://opensource.org/licenses/EPL-2.0)

[![Docker Repository on Quay](https://quay.io/repository/projectopenubl/searchpe/status "Docker Repository on Quay")](https://quay.io/repository/projectopenubl/searchpe)
[![Docker Repository on Quay](https://quay.io/repository/projectopenubl/searchpe-enterprise/status "Docker Repository on Quay")](https://quay.io/repository/projectopenubl/searchpe-enterprise)

[![Project Chat](https://img.shields.io/badge/zulip-join_chat-brightgreen.svg?style=for-the-badge&logo=zulip)](https://projectopenubl.zulipchat.com/)

# Searchpe

Microservicio que expone los datos provenientes del `padrón reducido` de la SUNAT.

## Ejecutar en modo desarrollo

### Iniciar servidor

Puedes ejecutar la aplicación en modo desarrollo con:

```shell script
./mvnw compile quarkus:dev -Pstandalone-dev
```

### Iniciar UI

Instala las dependencias npm:

```shell
yarn --cwd src/main/webapp install
```

Inicia la UI en modo desarrollo:

```shell
yarn --cwd src/main/webapp run start
```

## Links

- [Documentación](https://project-openubl.github.io)
- [Discusiones](https://github.com/project-openubl/searchpe/discussions)

## License

- [Eclipse Public License - v 2.0](./LICENSE)
