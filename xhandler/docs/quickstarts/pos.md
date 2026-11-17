# Punto de venta: boletas

- **Una serie por caja** (`B001`, `B002`, …): `POST …/companies/{c}/series
  {"tipo_comprobante": "03", "serie": "B002"}`. Cada caja emite sin esperar a las demás.
- Envíe `"serie": "B002"` en cada emisión y `"delivered": true` si entrega el ticket impreso
  en el momento (o configure `delivered_on_emit_default` en la empresa).
- Para tickets impresos: `PUT …/companies/{c}/settings {"pdf_format": "ticket80"}` y
  descargue `…/files/pdf`.
- **Boletas mayores a S/ 700** deben identificar al comprador (DNI o RUC).
- No se preocupe por el resumen diario: las boletas se envían una por una, y si alguna no
  queda aceptada en 5 días, el servicio la incluye automáticamente en un resumen diario.
- Si SUNAT no responde, la caja sigue emitiendo: los documentos quedan guardados y se envían
  cuando SUNAT vuelva, por orden de plazo.
