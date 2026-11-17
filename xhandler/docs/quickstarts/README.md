# Guías rápidas

Para integrar su sistema con xhandler-service (DESIGN.md §26.2). Toda empresa nueva empieza
en el **ambiente de pruebas** (el simulador de SUNAT): emita sin miedo, nada llega a SUNAT.

- [ERP: facturas y notas](erp.md)
- [Punto de venta: boletas, una serie por caja](pos.md)
- [Webhooks: enterarse de la respuesta de SUNAT](webhooks.md)

Referencia de la API: `/q/swagger-ui` y `/q/openapi` en su instancia. SDKs generados para
TypeScript y Java: ver [`sdk/`](../../sdk/README.md).

## Autenticación

Su sistema usa un cliente OAuth2 (client credentials) de Keycloak con audiencia
`xhandler-service`. Agréguelo como miembro de su cuenta con rol `operator`:

```bash
TOKEN=$(curl -s https://sso.ejemplo.pe/realms/xhandler/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=mi-erp -d client_secret=... | jq -r .access_token)
```

## Ambientes

| Ambiente | Para qué | Cómo |
|---|---|---|
| `fake` (por defecto) | Desarrollo: respuestas inmediatas y deterministas | Ponga `xhandler-fake:reject` (u `observe`, `exception`, `collision`) en las observaciones para simular respuestas |
| `beta` | Pruebas contra SUNAT beta | `POST …/companies/{id}/environment {"sunat_env":"beta"}` |
| `production` | Comprobantes reales | Requiere RUC verificado (usuario SOL), certificado digital y credenciales SOL |
