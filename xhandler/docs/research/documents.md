# SUNAT document rules and deadlines: requirements for xhandler-service

Research date: 2026-11-13. Sources are the SUNAT resolutions downloaded to `/tmp/sunat-regs/documents/` (text in `/tmp/sunat-regs/txt/`) and the local SUNAT validation rules `AjustesValidacionesCPE` v2026-02-12 (`.claude/agents/AjustesValidacionesCPE/*.md`, cited below as "Validation").

Abbreviations: RS = Resolución de Superintendencia. SEE-Del contribuyente = RS 097-2012/SUNAT. SEE-OSE = RS 117-2017/SUNAT. SEE-SFS = RS 182-2016/SUNAT. SEE-SOL = RS 188-2010/SUNAT. SEE framework = RS 300-2014/SUNAT. RCP = Reglamento de Comprobantes de Pago, RS 007-99/SUNAT.

How the sources were read:
- RS 097-2012, RS 300-2014 and RS 274-2015 are image scans with no text layer, so I could not read them directly. Their current wording comes from the amending resolutions that quote the full new text (RS 114-2019, 193-2020, 000003-2023, 000123-2022, 000165-2021, 000302-2024, 000048-2026, 113-2018).
- RS 117-2017 and RS 182-2016 have a text layer and were read directly, together with their amendments.
- I found no consolidated text of the RCP. sunat.gob.pe/legislacion/superin/1999/007.htm only links the original 1999 PDF. RCP content below comes from amending texts or is marked unverified.

---

## Deadlines table (law in force on 2026-11-13, plus changes already published)

"D" is the issue date (fecha de emisión) written in the document. Unless noted, periods are calendar days counted from the day after D.

| Document | Send deadline (SUNAT or OSE) | Voiding / cancellation deadline | Source |
|---|---|---|---|
| Factura (01) and the NC/ND linked to it | On D, or up to **3 calendar days** after D. After that it does not count as a factura or nota electrónica, even if it was delivered to the buyer. | **Comunicación de baja (RA)**: today, up to the 7th calendar day after the day the accepted CDR was received (097-2012 art. 14.1 b). SUNAT's validator rejects bajas sent more than 7 days after D (code 2957). **From 2027-01-01**: up to the 7th calendar day after the document's generation date (097-2012 art. 14.1 b; 117-2017 art. 17.2 b; 182-2016 art. 13.1 b). | RS 000003-2023 arts. 3–5, which amend 097-2012 art. 12.1 c), 182-2016 art. 12.2 and 117-2017 art. 15.1 a). Baja: RS 114-2019 art. 2.5; RS 000048-2026 arts. 9–11, in force from 2027-01-01 per RS 000143-2026. Validation codes 2108 and 2957. |
| Boleta (03) and its NC/ND, sent in a **resumen diario (RC)** | On D, or up to the **7th calendar day** after D. Several RC files may cover the same day of issue. An RC sent after the period is treated as a declaración rectificatoria. | Re-send the line in an RC with estado "anulado" (estado 3). Today: up to the 7th day after the CDR / CDR-resumen was received; if the document was never sent, up to the 7th day after its generation. **From 2027-01-01**: always up to the 7th day after generation. The validator rejects estado 3 lines when the RC arrives more than 7 days after D (code 2957). | 097-2012 art. 21.1 and art. 14.2 (RS 114-2019 art. 2); 117-2017 art. 23.1 and art. 17.4 (RS 114-2019 art. 4); RS 000048-2026 arts. 9 and 11. Validation codes 4443 (observation) and 2957. |
| Boleta and its notes, **sent individually** | On D, or up to the **5th calendar day** after D. After that only the resumen diario is allowed. | Same as the resumen diario row (anulado in an RC). | 097-2012 art. 12, 2nd paragraph (RS 114-2019 art. 2.3); 117-2017 art. 15.2 b) (RS 114-2019 art. 4.3). Validation code 1079, "more than 5 days" for series starting with B. |
| DAE (12 etc.), recibo SP and their notes | Today: up to 7 calendar days after D. **From 2027-01-01**: 3 calendar days. | RA, same rule as the factura. | RS 000003-2023 art. 3 (097-2012 art. 12.1 a); RS 000048-2026 art. 9 (097-2012 art. 12.1 a) and art. 11 (117-2017 art. 15.1). |
| Liquidación de compra (04) | On D or the next calendar day. | Resumen de reversiones (RR), type 04: at most 7 days after D, and only if no payments are registered (validation codes 2958 and 2471; this check does not apply in SEE-OSE). | RS 000003-2023 art. 3 (097-2012 art. 12.1 b). |
| GRE remitente (09) / GRE transportista (31) | **Before transport starts.** In SEE-Del contribuyente the emitter must hold a CDR with status *aceptada* before the transport starts. GRE can no longer be issued through an OSE. | Baja through SUNAT Operaciones en Línea (SOL) only: (a) before transport starts, or (b) when the destinatario changes during transport. A GRE cannot be voided once a GRE-por-evento has been issued, and a GRE-por-evento itself can never be voided. | RS 000123-2022 art. 5 (097-2012 arts. 33–35) and art. 3 (255-2015 arts. 4 and 8). The OSE scope is cut down by 000123-2022 art. 6 (117-2017 art. 3.1) and its derogatory provision 1.6. |
| Comprobante de retención (20) / percepción (40) | Up to **7 calendar days** after D (SEE-OSE). | **Resumen diario de reversiones (RR)**: within 7 calendar days counted from the day after the reversal was communicated to the provider or client. One RR covers a single day. | 117-2017 arts. 36 and 38.1. The SEE-Del contribuyente rule is in RS 274-2015, which is a scan: unverified. |
| Paper documents issued in contingency (declaración jurada informativa) | On the day of issue, or up to the 7th calendar day after it. Sent with the electronic XML format of the matching document, or in the "resumen diario BVE-NE" for boletas. | Baja requests can go in the same resumen BVE-NE (boletas). | 300-2014 art. 4.2.4 b.1 (RS 113-2018). |
| NC used **exceptionally** to cancel a factura issued to the wrong person, or to fix the description or the crédito data | SEE-SOL: up to the 10th business day of the month after the factura's issue month. SEE-OSE: the old 15-business-day limit (117-2017 art. 26.5) was derogated. | n/a | RS 193-2020 art. 2.2 (188-2010 art. 13.1.7); RS 000165-2021, derogatory provision. |
| Factura conformidad (DU 013-2020) | SUNAT makes the factura available on the platform on the day of the accepted CDR (the next day if the buyer is a State entity). | The buyer has 8 calendar days to register conformidad or disconformidad. A factura that is voided, or cancelled with an NC, before conformidad drops out of the process. | RS 000165-2021 arts. 3.3, 3.4, 3.6 and 4.1. |

---

## 1. Deadlines for sending documents to SUNAT/OSE, and what happens when late

**1.1 Factura and its notes: 3 calendar days.**
- History. RS 114-2019 set 7 days. RS 193-2020 cut it to "D or the next calendar day", originally from 2021-04-01. The cut was postponed by RS 000150-2021 (to 2022-01-01), RS 000201-2021 (to 2022-07-01) and RS 000117-2022 (to 2023-01-01). During the postponement a transitional 3-day rule applied (000150-2021 transitional provision, extended by 000201-2021 and 000117-2022 to 2022-12-31).
- RS 000003-2023 (published 2023-01-05, in force the next day) made **3 calendar days** permanent:
  - 097-2012 art. 12.1 c): "en la fecha de emisión ... o, incluso, hasta en un plazo máximo de tres días calendario contado desde el día calendario siguiente a esa fecha";
  - 182-2016 art. 12.2;
  - 117-2017 art. 15.1 a).
- RS 000302-2024 (6th complementary provision) restated the same 3 days in 117-2017 art. 15.1, and RS 000048-2026 art. 11 restates it again from 2027.
- Note: the "000189-2021" mentioned in RS 000201-2021 and 000117-2022 is the 2022 tax-calendar resolution. It is not about sending deadlines.
- **Effect of sending late**:
  - 182-2016 art. 12.2 and 097-2012 art. 12: "Vencido ese plazo lo no enviado no tendrá la calidad de factura electrónica ni de nota electrónica, aun cuando hubiera sido entregada al adquirente o usuario."
  - 117-2017 art. 15.1: "el OSE no puede realizar la comprobación material ... lo que reciba no tendrá la calidad de factura electrónica".
  - Validator: error 2108 "Presentación fuera de fecha" when reception date minus D exceeds the current maximum (parameter 004 "Plazo máximo de envío"). It applies only to series that do not start with a digit.
- **Back-dating**: D may be earlier than the date art. 5 of the RCP requires, if the issuer chooses to issue in advance (097-2012 art. 12; 117-2017 art. 15.1).
- **Future-dating**: rejected (code 2329) when D is more than 2 days after the send date. Seen for factura, boleta, NC, ND, DAE and liquidación de compra.
- Requirement for xhandler-service:
  - Compute each document's deadline as D + 3 calendar days, in Lima time (America/Lima).
  - Alert before the deadline and block or flag submissions after it.
  - Keep D as given (back-dating allowed), and reject D more than 2 days ahead of the send date.
  - When acting as OSE, reject late facturas and notas with code 2108 and issue a comunicación de inconsistencias.
  - Store D, the send timestamp and the CDR timestamp separately.

**1.2 Sujetos sin capacidad operativa (DL 1532). Effective 2024-12-31 (RS 000302-2024).**
- Taxpayers on the published SSCO lists cannot issue facturas or their notes through the SEE (300-2014 art. 1, last paragraph).
- What they send "no tendrá dicha calidad" (182-2016 art. 12.2).
- The OSE cannot check them and may only check what they are still allowed to issue (117-2017 arts. 7.4, 13.1 h and 15.1).
- Liquidaciones de compra are excepted.
- Requirement for xhandler-service:
  - Check each tenant's RUC against the SSCO list (or the SUNAT padrón) before issuing or validating facturas and notas.
  - In OSE mode, refuse them.

**1.3 Boleta and its notes.**
- *Resumen diario*:
  - Send on the day of issue, or up to the 7th calendar day after it (097-2012 art. 21.1 b; 117-2017 art. 23.1 b, as amended by RS 114-2019).
  - Each RC covers one day of issue; several RCs for the same day together form one declaración jurada.
  - A later RC replaces earlier ones only for the boletas it includes. It counts as a sustitutoria if sent within the period, and as a rectificatoria if sent after.
  - The validator only warns (observation 4443, "informado fuera del plazo") when D is more than 7 days before the RC's reception.
- *Individual sending* (optional): up to the 5th calendar day after D. After that only the RC is allowed (097-2012 art. 12, 2nd paragraph; 117-2017 art. 15.2 b; validator code 1079 "Solo puede enviar el comprobante en un resumen diario").
- In SEE-OSE, the emitter registers in SOL which method (RC or individual) it uses and from/to which dates (117-2017 art. 15.2 a–b, original text).
- Requirement for xhandler-service:
  - Support both paths for each tenant and route automatically: individual send while D+5 has not passed, otherwise RC until D+7.
  - Generate RC files grouped by day of issue.
  - Track replacement or rectification per boleta.
  - Warn on any boleta not reported by D+7.

**1.4 GRE.** See §6. It must be sent, and have an accepted CDR, before transport starts.

**1.5 Percepción/retención.** Up to 7 calendar days after D. Late documents are not CRE/CPE, and the OSE issues a comunicación de inconsistencias (117-2017 arts. 36–37; validator 2600 "enviado fuera del plazo permitido"). See §7.

**1.6 OSE-side timing.**
- The OSE must send each CDR, together with the checked document, to SUNAT **within one hour** of checking it (117-2017 art. 7.5, RS 114-2019 art. 4.1).
- It must keep the document, the CDR and any comunicación de inconsistencias for **one month** (art. 7.12).
- It must check documents "de forma inmediata" (art. 7.4, RS 000302-2024).
- Validator codes on the SUNAT side: 2804, 2809, 2810, 2876 and 2950 ("presentado fuera de plazo").
- Requirement for xhandler-service (OSE mode): forward the CDR and document to SUNAT within 60 minutes, with monitoring and retries, and retain them for at least one month.

**1.7 Sanctions.**
- 117-2017 art. 8 (original text, read): an OSE that fails to send SUNAT the document and its CDR is fined 25 UIT per occurrence. An OSE that does not check the documents it issues itself is also fined 25 UIT. Breach of reserva tributaria leads to removal from the register.
- Later OSE infractions and gradualidad (RS 191-2019) were not downloaded.
- Infractions for the emisor (Código Tributario art. 174 and related) were not researched: see Unverified.

---

## 2. Voiding: comunicación de baja, resumen diario "anulado", resumen de reversiones

**2.1 Only documents not yet delivered can be voided.**
- 097-2012 art. 14: "El emisor electrónico puede dar de baja la numeración de los documentos **no otorgados** aun cuando se hubiera generado ... la CDR respectiva" (RS 114-2019 art. 2.5).
- 117-2017 art. 17.1: same rule, for documents that were sent or reported to the OSE.
- A document that has already been delivered must be corrected or cancelled with a nota de crédito instead (§2.5).

**2.2 Factura, DAE and their notes: comunicación de baja (RA).**
- An RA file may list several documents, but all must have been issued on the same day.
- Today (097-2012 art. 14.1 b): up to the 7th calendar day after the day the accepted CDR was received.
- 117-2017 art. 17.2 sets no explicit deadline today. The SUNAT validator, however, rejects with **2957** when the RA arrives more than 7 days after the document's issue date (series that do not start with a digit). Other rejections:
  - document not registered: 2105;
  - document was rejected: 2398;
  - already voided: 2323;
  - D does not match the referenced issue date: 2375.
- Allowed document types in an RA: 01, 07, 08, 30, 34, 42.
- Do not send an RA for documents that were never sent to SUNAT (097-2012 art. 14.1, last paragraph).
- **From 2027-01-01** (RS 000048-2026 arts. 9, 10 and 11, postponed by RS 000143-2026): the RA deadline is the 7th calendar day after the **generation date** of the voided document, in SEE-Del contribuyente, SFS and OSE. In OSE it becomes a formal CDR condition: the OSE must reject late RAs with a comunicación de inconsistencias.
- Requirement for xhandler-service:
  - Offer baja only for documents in "accepted, not delivered" state, and inside D+7.
  - Group RA files by issue date.
  - Track RA tickets and their CDRs.
  - In OSE mode, apply the D+7 check now (matching SUNAT's validator) and as a legal condition from 2027.

**2.3 Boleta and its notes: resumen diario with estado "anulado".**
- Include the line in an RC for the boleta's day of issue, with estado 3 "anulado" (097-2012 art. 14.2; 117-2017 art. 17.4).
- Today:
  - documents already sent or reported: up to the 7th day after the CDR / CDR-RC was received;
  - documents never sent: up to the 7th day after generation.
- From 2027: up to the 7th day after generation in all cases.
- The validator rejects estado-3 lines when the RC arrives more than 7 days after D (code 2957).
- Requirement for xhandler-service: implement voiding of boletas as an RC with estado 3 inside D+7. Do not use an RA for boletas (B series).

**2.4 CRE/CPE (and liquidación de compra): resumen diario de reversiones (RR).**
- Deadline: within 7 calendar days after the reversal was communicated to the provider or client. One RR per day.
- A later RR of the same type for the same day, sent within the period, replaces the earlier one (117-2017 art. 38.1–38.3).
- Validator: allowed types are 20 and 40 (and 04 outside OSE); series must match R### or P###; 2751 if the document was already reverted; 2750 if it does not exist.
- Requirement for xhandler-service: an RR workflow with a 7-day timer starting at the "reversal communicated" event, which must be recorded in the system.

**2.5 Alternative: nota de crédito.**
- An NC applies to documents that were already delivered to the same buyer:
  - 117-2017 art. 26.1–26.2: a factura or DAE with a CDR, or a boleta (with a CDR if it was sent individually);
  - 097-2012 art. 22.1 (RS 000048-2026 text): a factura, DAE or recibo SP with an accepted CDR, or a boleta.
- Exceptional NCs (117-2017 art. 26.4; 097-2012 art. 22.3):
  - **cancel** a document issued to a person other than the real buyer. The document is then treated as never issued, and its number cannot be reused;
  - correct a wrong goods or service description;
  - correct the forma-de-pago / crédito data. RS 193-2020 extended the list of correctable items to 64-A, 64-B and 88–91 of annex 1, and 65–68 of annex 2.
- For facturas covered by DU 013-2020, an NC can record how a claim was handled, or change items 64-A/64-B, **only until conformidad occurs** (097-2012 art. 22.4; 117-2017 art. 26.4.3; RS 000165-2021).
- **Change from 2027-01-01**: "Una nota de crédito electrónica puede modificar **una** factura electrónica, una boleta..." (117-2017 art. 26.2, 27.2 and 097-2012 art. 22.1, 23.1, RS 000048-2026). The previous text said "una o más". Read literally, this ends notes that reference several documents.
- Requirement for xhandler-service:
  - Expose the decision as "void if not delivered and inside D+7, otherwise issue an NC".
  - Block NC types 04–05 and the claim-attention NC after conformidad.
  - Plan for a single referenced document per NC/ND from 2027.

**2.6 GRE baja.** Through SOL only, under the conditions in §6.

---

## 3. Boleta: buyer identification, "clientes varios", issue vs send dates

- **S/ 700 threshold**:
  - SUNAT orientation FAQ (orientacion.sunat.gob.pe/01-preguntas-frecuentes-comprobantes-de-pago): when "el monto total a pagar, en la boleta de venta supere los S/ 700", the buyer's full name and identity-document number are required. The FAQ cites no article.
  - Validator: resumen diario error **2514**, "Si el campo 'Importe total de la venta' es mayor a 700 nuevos soles y no existe el tag" (buyer node). Return code 2396 is listed in the code catalog ("Si el monto total es mayor a S/. 700.00 debe consignar tipo y numero de documento del adquiriente"), but it is not referenced in the Boleta 2.0 rules.
  - The RCP article that sets the threshold could not be read (believed to be art. 8, but unverified).
- **"Clientes varios" / no identification**:
  - The resumen diario accepts buyer document type "-" (validation 2016: "diferente al listado y guión (-)").
  - The local factura guide notes that "-" is used for B2C sales under S/ 700.
  - In an individual boleta the buyer `PartyIdentification` is still required (errors 2014 and 2015). Formats checked: RUC is 11 digits (2017); DNI is 8 digits (only an observation, 4207); other types are alphanumeric up to 15 characters.
- **Small sales**: boletas are not required for sales to final consumers of S/ 5.00 or less, unless the buyer asks for one. The issuer must then issue, at the end of the day, a boleta that consolidates those sales (117-2017 art. 21.1; 097-2012 art. 19.1 per RS 340-2017).
- **Issue date vs send date**: see §1.3. D may be earlier than the date art. 5 of the RCP requires, if the issuer anticipates the issue (097-2012 art. 12). D more than 2 days in the future is rejected (2329).
- **Use of the boleta**: it does not give the right to crédito fiscal or support costs or expenses unless a rule allows it (117-2017 art. 21.2). It cannot support a transfer of goods except in the RCP cases listed (art. 21.3).
- Requirement for xhandler-service:
  - Require buyer document type and number when the boleta total exceeds S/ 700 (PEN equivalent).
  - Allow "-" / varios below that amount.
  - Support daily consolidated boletas for sales of S/ 5 or less.

---

## 4. RCP and SEE essentials that affect an electronic system

- **Series and numbering** (validator, Factura/Boleta 2.0 item 3; RA item 11):
  - Format `<serie>-<número>`. Factura series `F[A-Z0-9]{3}`, boleta series `B[A-Z0-9]{3}`; NC/ND series follow the document they modify.
  - Retención `R###`, percepción `P###`, liquidación `L###`.
  - The correlative is 1–8 digits.
  - A series made only of digits (`[0-9]{1,4}`) marks a paper or contingency document. It must exist in SUNAT's list of authorized physical documents (3207 / 4204), and deadline checks do not apply to it.
  - Numbering must not have been used before (097-2012 art. 10; 117-2017 art. 13.1 d). The validator returns 1033 for a number registered with other data, and 1032/2223-type codes for duplicates.
  - A number cancelled with an NC (wrong buyer) cannot be reused for the same document type (117-2017 art. 26.4.1).
  - Requirement for xhandler-service: keep a per-tenant, per-series atomic correlative that is never reused. Keep separate numeric series for contingency paper.
- **Required data**: given by annexes 1–4 of 097-2012 (minimum requirements and issue conditions), which the validation rules mirror. Key additions:
  - forma de pago contado/crédito, net amount pending and instalments (RS 193-2020: annex 1 items 64-A/64-B, in force for SEE from 2021-04-01);
  - the ICBPER bag tax (RS 150-2019, not read).
  - Requirement for xhandler-service: generate XML that passes AjustesValidacionesCPE, and treat changes to these annexes as versioned rules.
- **Issue timing**: governed by RCP art. 5 (117-2017 art. 14.1). For transactions agreed online or by phone and paid in advance by card or transfer, the comprobante is issued and delivered on the date the payment is confirmed or received. The text of RCP art. 5 itself was not read.
- **Contingency** (300-2014 art. 4, RS 113-2018):
  - A SUNAT-designated issuer that cannot issue electronically for reasons it is not responsible for may issue on preprinted paper authorized for an imprenta.
  - The paper must carry the legend "Comprobante de pago emitido en contingencia" and the phrase "Emisor electrónico obligado".
  - The issuer must send a **declaración jurada informativa** (the "resumen de comprobantes impresos") on the day of issue, or up to the 7th calendar day after it. It uses the electronic XML format of the matching document, or the resumen diario BVE-NE for boletas, and goes to SUNAT or through the OSE. An accepted CDR is required.
  - New authorizations of contingency forms require that at least 90% of previously authorized forms were reported.
  - **From 2027-01-01** (RS 000048-2026 art. 8): designated issuers can no longer use documentos autorizados (RCP art. 4 num. 6.1 b, c, k) in place of a factura or boleta. They must use printed factura or boleta forms.
  - Requirement for xhandler-service:
    - Support registering contingency paper ranges (numeric series).
    - Capture paper documents after the fact and send their XML declaration within D+7.
    - In OSE mode, accept numeric-series documents and apply the authorization checks.
- **Exports**:
  - A factura may be issued to a non-domiciled buyer showing only the buyer's name (117-2017 art. 19.2 a).
  - Delivery goes through the means the buyer defines (art. 18.3).
  - An NC on an export may modify a factura or a boleta (097-2012 art. 22.1, RS 340-2017).
  - Validator: tipo de operación 0200–0208 and 0401 rules on buyer document type (2800).
- **Anticipos and gratuitas**:
  - Handled at XML level. Validator: anticipo fields must reference a factura (02) or boleta (03) (2505), the amount must be > 0 (2503), and global discount codes 04/05/06/20 require a PrepaidAmount (3282).
  - Free transfers use tax code 9996 with special afectación rules (3110).
  - No resolution text specific to these topics was read.
- **Facturas to tourists (Tax Free, tipo de operación 2106)**: RS 000005-2023 (title only in the index; not read). Validator: buyer document type must be 7, B or G.
- **Changes in 2024–2026**:
  - RS 000032-2024: notes on air tickets (title only).
  - RS 000048-2026: airport DAE and documento de atribución become electronic, in force 2027-01-01 and 2027-04-01 per RS 000143-2026.

---

## 5. Who must issue electronically; delivery to the buyer; keeping documents available

- **Designation**:
  - Most taxpayers are designated by successive resolutions (RS 155-2017, 279-2019 and earlier).
  - **RS 000075-2026** (in force 2026-06-01): new RUC registrants in the MYPE Tributario, Especial or General income-tax regimes become electronic issuers **from the day they register in the RUC**. Taxpayers leaving the Nuevo RUS become issuers from the 1st day of the following month. Both are also required to keep SIRE records from then on.
  - Since RS 000123-2022 (art. 7, annex X), GRE issuers are designated in the same way.
  - Requirement for xhandler-service: tenant onboarding must assume obligation from day one, with no grace period for paper.
- **Delivery (otorgamiento)** (117-2017 art. 18; 097-2012 art. 15, RS 114-2019):
  - *Factura and its notes* count as delivered when handed over or made available by electronic means. A printed representation is allowed only for income-tax art. 46 operations, by agreement.
  - *Boleta and its notes* count as delivered when sent electronically; once chosen, the same electronic channel must be kept unless the buyer agrees to change. A printed representation is allowed if both parties agree.
  - The electronic channel is chosen by the issuer.
  - A document counts as available from (a) the moment it lands in the email address the buyer designated, or (b) the moment it can be downloaded from the issuer's website.
  - A printed courtesy copy handed over in addition to electronic delivery is *not* a representación impresa (art. 18.5).
  - Requirement for xhandler-service:
    - Email delivery to a buyer-designated address, and/or an authenticated download portal.
    - Record the delivery or availability timestamp, which is the moment of otorgamiento.
- **Availability for one year**:
  - 097-2012 art. 25 (RS 252-2019 art. 3.10): "el emisor electrónico debe poner a disposición del adquirente o usuario, a través de una página web, los comprobantes ... por el plazo de un año, contado desde la emisión", with authentication so that only the buyer can access them.
  - The same applies to the OSE system through 117-2017 arts. 12.3 d) v and 29.2.
  - For CRE/CPE: 117-2017 art. 33.4 (one year, web page, authentication).
- **Lost copies**: on loss of a document, the buyer may ask the issuer for a new copy or representation (097-2012 art. 26; 117-2017 art. 31.2 per RS 000048-2026).
  - Requirement for xhandler-service: an authenticated buyer portal per tenant, holding XML, CDR and PDF for at least one year after issue, plus re-sending on request.
- **Storage**:
  - In SEE-OSE, SUNAT stores the documents on the emitter's behalf, except boletas reported only through a resumen diario (117-2017 art. 12.5).
  - Storage may be on magnetic or optical media (097-2012 art. 25).
  - The longer Código Tributario retention period was not researched (see Unverified).
- **Representación impresa**:
  - It must carry the **QR code** (only the QR since 2018-01-01; 117-2017 art. 12.3 d) iv; RS 340-2017).
  - Thermal or other paper must keep the data legible for at least 1 year (117-2017 art. 12.3 d) iii).
  - The airport DAE is exempt from the QR from 2027 (RS 000048-2026).
- **OSE consultation service**: the OSE must offer a web page where issuers and buyers can check whether a factura, DAE or nota is valid (CDR or inconsistency) during the 1-month retention period (117-2017 art. 7.15, RS 000123-2022).

---

## 6. GRE (RS 000123-2022 and later)

- **Scope**:
  - GRE remitente, GRE transportista and GRE por evento (255-2015 art. 1 num. 4).
  - Since RS 000123-2022 (in force 2022-07-13, with derogatory provision 1.6 from 2022-12-01), **SEE-OSE no longer issues GRE**: 117-2017 art. 3.1 lists only factura, boleta, DAE and notas.
  - GRE remitente and GRE transportista are issued in SEE-Del contribuyente (sent to SUNAT) or SEE-SOL.
  - GRE por evento is issued **only in SEE-SOL** (097-2012 art. 32).
- **Timing**:
  - GRE remitente: issued before the goods are handed to the carrier (255-2015 art. 4.2 a).
  - SEE-Del contribuyente: send to SUNAT **before transport starts**; "Antes del inicio del traslado el remitente o el transportista debe contar con la CDR con estado aceptada" (097-2012 art. 34). A GRE received late "no tendrá la calidad de GRE".
  - The art. 34 cross-reference to "inciso b) del numeral 12.1 del artículo 12" now points to the liquidación de compra deadline (D or the next day) after RS 000003-2023 renumbered art. 12.1. How that interacts is unclear (unverified).
  - The SUNAT CDR carries the data used to build the QR (097-2012 art. 35).
- **Rules**:
  - A GRE transportista needs a prior GRE remitente (097-2012 art. 32.3).
  - One GRE transportista covers a single remitente, destinatario, arrival point and vehicle. It may group several GRE remitente that share the destinatario and the departure and arrival points (255-2015 art. 3.3 b).
  - If transport is subcontracted, the subcontractor issues the GRE transportista (art. 3.7).
  - Planned transshipment rules: art. 3.6.
  - GRE por evento: issued after an event and before transport resumes (255-2015 art. 4.3, art. 7). Events: cannot reach the arrival point, transshipment, and **change of driver** (added by RS 000108-2026, in force 2026-06-01). It may also update the port "Cita u Orden de Entrega" number.
- **Delivery** (255-2015 art. 4.6), by electronic means:
  - GRE remitente to the destinatario at the latest when transport ends; to the carrier (public transport) at the latest when the goods are handed over;
  - GRE transportista to the remitente before, during or at the end of transport.
  - In SEE-Del contribuyente, only after an accepted CDR. Access must be restricted to the parties.
- **Supporting the transport in the field** (255-2015 art. 6): show the QR (printed or digital), or give the RUC, series and number. In SEE-Del contribuyente this works only with an accepted CDR.
- **Baja** (255-2015 art. 8): through SOL with clave SOL, (a) before transport starts, or (b) when the destinatario changes mid-route; then issue a new GRE. Not possible once a GRE por evento exists, and a GRE por evento can never be voided.
- **No conformidad** (art. 9): the destinatario (with RUC) or a carrier not required to issue may register disagreement in SOL until the 7th calendar day of the month after issue. After that the data are presumed correct.
- **Changes 2024–2026**:
  - RS 000240-2024 (in force 2024-11-14): foreign-trade traceability fields.
  - RS 000108-2026 (in force 2026-06-01): customs agency as obligated remitente; the "Cita u Orden de Entrega" document; ZED and ZOFRATACNA documents; optional container data for multi-vehicle imports; annexes 8, 12, 13, 14 and 28 of 097-2012 amended.
- **REST API and OAuth**:
  - Not in any resolution read. The SUNAT GRE manual page (cpe.sunat.gob.pe) returned 403.
  - In this repo, `xsender/src/rest.rs` uses an OAuth token from `https://api-seguridad.sunat.gob.pe/v1/clientessol/{client_id}/oauth2/token/` (scope `https://api-cpe.sunat.gob.pe`) and submits to `https://api-cpe.sunat.gob.pe/v1/contribuyente/gem`.
  - Requirement for xhandler-service:
    - Send GRE only directly to SUNAT through REST, acting as the tenant or as its PSE, never in OSE mode.
    - Gate "ready to ship" on an accepted CDR.
    - Render the QR from the CDR data.
    - Expose baja and GRE por evento as SOL-only (outside the API), or document them as manual steps.
    - Validate the new 2026 fields.

---

## 7. Percepción / retención (RS 274-2015 and amendments), briefly

- RS 274-2015 is a scan (no text). The OSE rules (117-2017 Title IV, arts. 32–38) were read:
  - certificate and OSE registration are required first (art. 32);
  - the emitter must send each CRE/CPE and the resumen diario de reversiones to the OSE, and send SUNAT the resumen diario of CRE and CPE referred to in 300-2014 art. 4.6, which covers paper CRE/CPE (art. 33.3);
  - a buyer or provider portal for one year (art. 33.4);
  - issue conditions (art. 34): unused, not reverted, formato digital per annexes 15/16, sent within the deadline;
  - send within 7 calendar days after D (art. 36);
  - reversals via RR within 7 days (art. 38).
- Validator: Percepciones 1.0 and Retenciones 1.0 sheets. Issuer must be an active agente de percepción/retención (2601, 2617). Collection or payment dates must fall in the same month and period (2612, 2625, 2659, 2661). Late send: 2600.
- RS 126-2021 changed the percepción document for fuel and goods sales (title only; not read).
- Requirement for xhandler-service:
  - CRE/CPE issuance with a 7-day send timer.
  - Validate agent status and the regime rate (parameters 002/003).
  - RR workflow.
  - One-year portal.

---

## Unverified / could not access

- **RS 097-2012 (base), RS 300-2014 and RS 274-2015**: image scans, no OCR available. Their current wording comes from amending texts. Not verified: the SEE-Del contribuyente rules for CRE/CPE (send deadline, reversal), and the exact current numbering of 097-2012 art. 12 for boletas (12.2?).
- **RCP (RS 007-99) consolidated text**: not found online. Unverified: the article that sets the S/ 700 boleta threshold (believed to be art. 8), the rules on "clientes varios", RCP art. 5 (issue timing), art. 9 (series and printing), and art. 4 (cases).
- **Sanctions for late or missing sending by the emisor** (Código Tributario art. 174 num. 1–2, 15; tablas de infracciones): not researched. Later OSE/PSE infraction rules (RS 191-2019) not downloaded.
- **Document retention period** (Código Tributario art. 87 num. 7 and art. 43, prescription): not researched. Only the one-year buyer-portal rule and the OSE one-month rule were verified.
- **GRE REST/OAuth technical spec**: the SUNAT manual on cpe.sunat.gob.pe returned 403. Only the repo's existing client was observed.
- **The GRE deadline cross-reference** in 097-2012 art. 34 ("inciso b) del numeral 12.1 del artículo 12") after RS 000003-2023 renumbered art. 12.1: unclear.
- **Value of validator parameter 004** ("plazo máximo de envío") per document type: not in the local files. The 3-day factura value is inferred from the resolutions.
- **Titles only, not read**: RS 000005-2023 (facturas to tourists), RS 000032-2024 (air-ticket notes), RS 126-2021 (percepción document), RS 150-2019 (ICBPER requirements), RS 254-2018 and 277-2019 (downloaded but not reviewed in depth).
- **Whether a factura can still be voided with an RA after conformidad**: RS 000165-2021 art. 3.6 only says voided facturas drop out of the conformidad process before conformidad. No explicit ban after conformidad was found.
- **The removed SEE-OSE limit for exceptional NCs**: the 15th business day of the following month (117-2017 art. 26.5) was derogated by RS 000165-2021. No current limit was found for SEE-OSE or SEE-Del contribuyente; only SEE-SOL keeps a 10th-business-day limit.
