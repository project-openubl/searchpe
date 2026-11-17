# ERP: facturas y notas

1. **Cree la empresa** (una vez): `POST /api/v1/tenants/{t}/companies` con `ruc` y `razon_social`.
   Las series por defecto (`F001`, `B001`, `FC01`, …) se crean solas.
2. **Emita** con una `Idempotency-Key` propia (por ejemplo, el id del documento en su ERP):

   ```bash
   curl -X POST https://api.ejemplo.pe/api/v1/tenants/$T/companies/$C/documents \
     -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
     -H "Idempotency-Key: erp-venta-10234" \
     -d '{
       "document": {"kind": "Invoice", "spec": {
         "cliente": {"tipo_documento_identidad": "6", "numero_documento_identidad": "20123456789", "nombre": "Cliente SAC"},
         "detalles": [{"descripcion": "Servicio", "cantidad": 1, "precio": 100}]}},
       "labels": {"erp_id": "10234"}
     }'
   ```

   La respuesta (`201`) trae el número asignado (`serie_numero`), el `digest_value`, el
   contenido del QR y el enlace para el comprador (`access_url`). El estado es
   `pending_send`: el servicio envía a SUNAT en segundo plano y reintenta solo.
3. **Si la red falla**, repita la misma solicitud con la misma `Idempotency-Key`: recibe el
   mismo documento, nunca uno duplicado.
4. **Consulte el resultado** con un webhook (recomendado, ver [webhooks.md](webhooks.md)) o
   con `GET /api/v1/tenants/{t}/documents?label=erp_id:10234`.
5. **Notas de crédito**: `kind: "CreditNote"` con `comprobante_afectado_serie_numero`; la serie
   (`FC01` o `BC01`) se elige según el documento afectado.
6. **Anular** una factura aceptada y no entregada (hasta el 7.º día):
   `POST …/documents/{id}/void {"motivo": "...", "not_delivered": true}`. Si ya la entregó,
   emita una nota de crédito.

Archivos: `GET …/documents/{id}/files/{xml|cdr|pdf}`. Línea de tiempo: `GET …/documents/{id}/events`.
