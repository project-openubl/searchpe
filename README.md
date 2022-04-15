![CI](https://github.com/project-openubl/searchpe/workflows/CI/badge.svg)
[![License](https://img.shields.io/badge/Apache-2.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)

[![Project Chat](https://img.shields.io/badge/zulip-join_chat-brightgreen.svg?style=for-the-badge&logo=zulip)](https://projectopenubl.zulipchat.com/)

# Searchpe

Microservicio que expone los datos provenientes del `padrón reducido` de la SUNAT.

## Ejecutar en modo desarrollo

### Iniciar servidor

Puedes ejecutar la aplicación en modo desarrollo con:

```shell script
./mvnw compile quarkus:dev
```

### Iniciar UI

Instala las dependencias npm:

```shell
npm install --prefix src/main/webapp
```

Inicia la UI en modo desarrollo:

```shell
npm run start --prefix src/main/webapp
```

## Links

- [Documentación](https://project-openubl.github.io)
- [Discusiones](https://github.com/project-openubl/searchpe/discussions)

## License

- [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
