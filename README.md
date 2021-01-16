![CI](https://github.com/project-openubl/searchpe/workflows/CI/badge.svg)
[![Docker Repository on Quay](https://quay.io/repository/projectopenubl/searchpe/status "Docker Repository on Quay")](https://quay.io/repository/projectopenubl/searchpe)

# searchpe

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/

## Running the application in dev mode

### Init PostgreSQL

PostgreSQL is used for the application:

```shell script
docker run -p 5432:5432 -e POSTGRES_USER=searchpe_username -e POSTGRES_PASSWORD=searchpe_password -e POSTGRES_DB=searchpe_db postgres:13.1
```

### Init server

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

## License

- [Eclipse Public License - v 2.0](./LICENSE)
