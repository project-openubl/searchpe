# Webhooks

```bash
curl -X POST https://api.ejemplo.pe/api/v1/tenants/$T/webhooks -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"url": "https://mi-erp.pe/xhandler", "events": ["document.*"]}'
```

La respuesta trae el secreto (`whsec_…`) una sola vez: guárdelo.

Cada evento es un CloudEvent (`application/cloudevents+json`): `document.accepted`,
`document.rejected`, `document.exception`, `document.voided`, `document.delayed`,
`document.rejected_after_delivery`, `document.void_rejected`.

**Verifique la firma** de la cabecera `X-Xhandler-Signature: t=<segundos>,v1=<hex>`:
`v1 = HMAC-SHA256(secreto, "<t>.<cuerpo>")`, y rechace `t` con más de 5 minutos.

```python
import hmac, hashlib, time
def valido(cuerpo: bytes, cabecera: str, secreto: str) -> bool:
    t, v1 = (p.split("=", 1)[1] for p in cabecera.split(","))
    esperado = hmac.new(secreto.encode(), f"{t}.".encode() + cuerpo, hashlib.sha256).hexdigest()
    return hmac.compare_digest(esperado, v1) and abs(time.time() - int(t)) < 300
```

Responda `2xx` en menos de 10 segundos; los fallos se reintentan (10 s, 1 min, 5 min, 30 min,
2 h, luego cada 6 h). Herramientas: `POST …/webhooks/{id}/test`, `GET …/webhooks/{id}/deliveries`
y `POST …/deliveries/{d}/redeliver`.
