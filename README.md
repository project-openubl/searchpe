![CI](https://github.com/project-openubl/xhandler-service/workflows/CI/badge.svg)
[![License](https://img.shields.io/badge/Apache-2.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Project Chat](https://img.shields.io/badge/zulip-join_chat-brightgreen.svg)](https://projectopenubl.zulipchat.com/)

# xhandler-service

Plataforma de comprobantes electrónicos SUNAT: emitir, firmar, enviar, entregar y reportar, como
software propio del emisor, PSE u OSE. Todo se construye, prueba y despliega junto.

| Directorio | Qué es |
|---|---|
| [xhandler](./xhandler) | Servicio multi-tenant de comprobantes (Quarkus + motor en Rust). Diseño: [DESIGN.md](./xhandler/docs/DESIGN.md) |
| [searchpe](./searchpe) | Consulta RUC sobre el padrón reducido de la SUNAT (Quarkus) |
| [ui](./ui) | La interfaz web única de ambos servicios (React + PatternFly) |
| [operator](./operator) | Operador de Kubernetes (OLM): tipos `XHandler` y `Searchpe` |

## Desarrollo

Requisitos: Java 25, Rust, Node 22, Go y Podman o Docker. Comandos y convenciones:
[AGENTS.md](./AGENTS.md).

```bash
podman compose -f hack/compose.yaml up -d                  # PostgreSQL, Keycloak (:8180), Grafana
hack/ui-e2e-stack.sh build && hack/ui-e2e-stack.sh up      # todos los servicios
(cd ui && cp .env.example .env && npm ci && npm run start:dev)   # UI en http://localhost:3000
```

Usuarios de prueba (realm `xhandler`): `alice/alice`, `bob/bob`; administradora `olga/olga`.

## Despliegue

Con el operador (OperatorHub): crea un `Searchpe` y un `XHandler`
([ejemplos](./operator/config/samples)). Imágenes: `ghcr.io/project-openubl/xhandler-service/*`
(`nightly` desde `main`, `vX.Y.Z` por release).

## Releases

Solo con el workflow
[release-xhandler-service](https://github.com/project-openubl/release-tools#releasing-xhandler-service)
de release-tools: verifica CI, crea el release y abre los PR de OperatorHub.

## Enlaces

- [Documentación](https://project-openubl.github.io) ·
  [Discusiones](https://github.com/project-openubl/xhandler-service/discussions)
- Licencia: [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)
