# Searchpe

Consulta RUC sobre el `padrón reducido` de la SUNAT. Sirve solo su API; la interfaz es la UI única
del repositorio ([ui/](../ui)).

```bash
./mvnw quarkus:dev -pl searchpe     # desde la raíz; Dev Services levanta PostgreSQL
```

Contra el stack local (`hack/compose.yaml`) y su realm de Keycloak, ver el
[AGENTS.md](../AGENTS.md) raíz y `hack/ui-e2e-stack.sh`.

Despliegue: con el operador, tipo `Searchpe` ([operator/](../operator)).

Licencia: [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)
