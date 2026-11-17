# Openubl UI

La interfaz web única de la plataforma: comprobantes electrónicos SUNAT
([xhandler-service](../xhandler)) y consulta del padrón RUC ([searchpe](../searchpe)).
React 19 + PatternFly 6, siguiendo el diseño de [pulp-ui-2](https://github.com/pulp/pulp-ui-2).

```bash
npm ci
cp .env.example .env
npm run start:dev   # http://localhost:3000
```

- Guía para desarrolladores y agentes: [AGENTS.md](AGENTS.md)
- Decisiones de diseño: [docs/adr/](docs/adr/)
- Variables de entorno: [docs/environment-variables.md](docs/environment-variables.md)
- Pruebas end-to-end: [e2e/README.md](e2e/README.md)

## Imagen

```bash
podman build -t openubl-ui .
podman run -p 8080:8080 \
  -e XHANDLER_API_URL=http://xhandler:8080 \
  -e SEARCHPE_API_URL=http://searchpe:8080 \
  -e OIDC_SERVER_URL=https://sso.example.com/realms/xhandler \
  openubl-ui
```

`SEARCHPE_API_URL` es opcional; sin él la UI oculta el padrón RUC. `GET /healthz` responde 200
para las sondas.
