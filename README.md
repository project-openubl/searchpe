![CI](https://github.com/project-openubl/searchpe/workflows/CI/badge.svg)
[![Docker Repository on Quay](https://quay.io/repository/projectopenubl/searchpe/status "Docker Repository on Quay")](https://quay.io/repository/projectopenubl/searchpe)
[![License](https://img.shields.io/badge/License-EPL%202.0-green.svg)](https://opensource.org/licenses/EPL-2.0)

[![Project Chat](https://img.shields.io/badge/zulip-join_chat-brightgreen.svg?style=for-the-badge&logo=zulip)](https://projectopenubl.zulipchat.com/)

# searchpe

Microservicio que expone los datos provenientes del `padrón reducido` de la SUNAT.

## Ejecutar en modo desarrollo

### Iniciar PostgreSQL

PostgreSQL puede ser iniciado con:

```shell script
docker run -p 5432:5432 -e POSTGRES_USER=searchpe_username -e POSTGRES_PASSWORD=searchpe_password -e POSTGRES_DB=searchpe_db postgres:13.1
```

### Iniciar Elasticsearch

```shell script
docker run -p 9200:9200 -p 9300:9300 -e discovery.type=single-node docker.elastic.co/elasticsearch/elasticsearch:7.10.2
```

### Iniciar servidor

Puedes ejecutar la aplicación en modo desarrollo con:

```shell script
./mvnw quarkus:dev
```

## Getting started

- [Documentación](https://project-openubl.github.io)
- [Discusiones](https://github.com/project-openubl/searchpe/discussions)

## License

- [Eclipse Public License - v 2.0](./LICENSE)
