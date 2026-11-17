# SUNAT emission systems and the PSE / OSE roles: requirements for xhandler-service

Research date: 2026-11-13. Sources are SUNAT resolution PDFs downloaded from
`https://www.sunat.gob.pe/legislacion/superin/YYYY/` (local copies and text in `/tmp/sunat-regs/roles/`,
plus `/tmp/sunat-regs/117-2017.pdf` and `/tmp/sunat-regs/199-2015.pdf`), the SUNAT yearly indexes
for 2014–2026 (`/tmp/sunat-regs/roles/idx/`), orientacion.sunat.gob.pe pages, and the local
validation rules in `.claude/agents/AjustesValidacionesCPE/` (v2026-02-12).

Citation conventions: "RS" = Resolución de Superintendencia. "Current" means no later amendment was
found in the 2014–2026 indexes. That is index-based only: I did not have a consolidated text, so
"current" means "not amended by anything I found". Wording marked as a quote was read directly in a PDF.

---

## 0. Map of the regulation (what amends what)

| Base norm | Subject | Amendments found and read (text) |
|---|---|---|
| RS 300-2014 | SEE umbrella (SEE-SOL, SEE-Del contribuyente, SEE-SFS, SEE-OSE...) | 117-2017 (adds SEE-OSE as art. 1 d), 287-2017, 340-2017, 113-2018 (repealed art. 4-A and 4.6), 202-2021, 114-2019 |
| RS 097-2012 | SEE-Del contribuyente | 199-2015 (PSE hooks, arts. 4 h, 6-B), 117-2017 (6-A, 6-B), 287-2017 (replaced arts. 6 and 6-A; homologation removed), 193-2020, 000003-2023 (factura deadline: 3 days), 123-2022 (GRE) |
| RS 199-2015 | PSE register | 117-2017 (2nd final provision and 7th modifying provision), 141-2017 (PSE-CF, arts. 9–11), 276-2017 (PSE-ME, arts. 12–14), 113-2018 (art. 3 c), 309-2018 / 277-2019 / 105-2020 / 221-2020 (ISO 27001 dates), 077-2021 (ISO grace period), **108-2022** (homologation removed, 5-day resolution, 90-day wind-down) |
| RS 117-2017 | SEE-OSE and OSE register | 287-2017, 340-2017, 092-2018 (national-service declaration, art. 6-A, 7.14), 113-2018, 239-2018 (art. 12.2), 242-2018 (7.15–7.17), 095-2019 (6.1 k, 7.18), 114-2019 (7.5, 7.12, 15.2, 17, 23, Annexes B/C), 133-2019, 191-2019 (sanctions overhaul), 193-2020 and 000003-2023 (15.1 deadlines), 123-2022 (GRE removed from SEE-OSE) |
| RS 239-2018 | Mandatory OSE and/or SEE-SOL for certain issuers | 044-2019, 105-2020, 221-2020 |

---

## 1. SEE structure (RS 300-2014) and SEE-Del contribuyente (RS 097-2012)

**Source limitation:** the PDFs of RS 300-2014 and RS 097-2012 are image scans with no text layer, and no
OCR tool was available. The findings below come only from later resolutions that quote or replace
their articles.

### 1.1 SEE structure
- RS 300-2014 art. 1 lists the systems that make up the SEE. RS 117-2017, 1st modifying complementary
  provision (1.1), added **"d) El Sistema de Emisión Electrónica Operador de Servicios Electrónicos"**.
  The recitals of RS 117-2017 and RS 340-2017 list SEE-SOL (RS 188-2010), SEE-Del contribuyente
  (RS 097-2012), SEE-SFS (RS 182-2016) and SEE-OSE (RS 117-2017). Current.
- RS 300-2014 art. 3.1, 2nd para. (as amended by RS 117-2017 1st mod. prov. 1.2): an issuer designated
  without a specific system must issue through the SEE "usando, a su elección, los sistemas que les estén permitidos".
- **Exclusivity of SEE-OSE:** RS 117-2017 art. 12.2 (replaced by RS 239-2018, sole modifying provision, in
  force 2019-03-01; last paragraph re-amended by RS 123-2022). Being an issuer under SEE-OSE "solo le permite emitir a
  través del SEE - OSE ... está impedido de emitir en sistemas de emisión distintos a este ..., salvo cuando
  se trate del SEE - SOL". Paper or ticket issuance stays possible where the rules allow it. Current.
  - Requirement for xhandler-service: for each company, store the emission system (SEE-Del contribuyente
    vs SEE-OSE). Once a company issues through an OSE, block direct SUNAT submission
    (SEE-Del contribuyente) for factura, boleta and notas. SEE-SOL (SUNAT's portal) remains the only
    allowed alternative and is outside our system.
- **Mandatory SEE-OSE and/or SEE-SOL:** RS 239-2018 sole article, replaced by RS 044-2019. Principal
  taxpayers (PRICOS) with annual income of 300 UIT or more must use SEE-OSE and/or SEE-SOL for factura, boleta and notas: from
  2019-03-01 (first cohort), and for later cohorts from July 1 of the year after they qualify. RS 105-2020 and
  RS 221-2020 postponed a 2019 cohort to 2022-01-01. Current, as far as found.
  - Requirement for xhandler-service: add a per-company flag "obligado a OSE" (PRICO and 300 UIT or more).
    For flagged tenants, the direct-to-SUNAT (SEE-Del contribuyente) path must be refused for
    invoices and notes.
- **GRE (despatch advice) is no longer in SEE-OSE:** RS 123-2022 art. 6 replaced RS 117-2017 art. 3.1 with "La factura
  electrónica, la boleta de venta electrónica, el DAE y la nota electrónica" and dropped GRE from 13.1 f/g, 15.1, 16 and 7.15.1.
  In force 2022-07-13. The 1st transitional provision allowed GRE-remitente through SEE-Del contribuyente or SEE-OSE under the old rules until 2022-11-30.
  - Requirement for xhandler-service: route GRE to SUNAT directly (GRE REST API, SEE-Del contribuyente),
    never through an OSE, including the tenant's own OSE role.

### 1.2 SEE-Del contribuyente: issuer using its own system
- RS 097-2012 art. 6 (replaced by RS 287-2017 art. 2). To become an issuer by choice, the taxpayer must:
  (6.1) opt in through SOL; (6.2) not be "no habido", not be suspended or deregistered, and be subject to 3rd-category income tax
  where applicable (Nuevo RUS added by RS 340-2017); and (6.3) meet one of two modalities.
  - **Modalidad I:** "Registrar a través de SUNAT Operaciones en Línea por lo menos un certificado digital
    vigente que será usado en la emisión electrónica", or reuse one already registered in any SEE system,
    plus register an e-mail address for receiving documents as acquirer.
  - **Modalidad II:** "Seleccionar a través de SUNAT Operaciones en Línea a uno o más proveedores de servicios
    electrónicos para autorizarlos a que realicen en su nombre cualquiera de las actividades inherentes...",
    plus the e-mail. An issuer under Modalidad II that also wants to perform some activities itself "debe tener
    registrado o registrar por lo menos un certificado digital vigente".
  - Art. 6-A (replaced by RS 287-2017): issuers designated by SUNAT follow the same 6.2/6.3 conditions.
  - RS 287-2017 removed the **homologation** process for SEE-Del contribuyente (see the 2nd transitional
    provision, "sin que deba cumplir el proceso de homologación"). Current.
- Who may sign. RS 117-2017 art. 1.17 defines "firma digital" (the same concept applies across the SEE) as the electronic signature of art. 6 of
  DS 052-2008-PCM, "que sea válida y esté vigente". The certificate "debe contar con los nombres y apellidos,
  la denominación o razón social y el número de RUC del titular"; for a natural person, also the ID type and number.
  The signature must belong to the issuer's RUC **or to the PSE authorized by it**. This appears in RS 117-2017 Annex C §III.a.5 and
  Annex B §3.2.c (current versions replaced by RS 114-2019 Annex XIII), and in the AjustesValidacionesCPE "General"
  sheet, error 2325: "ID del certificado del comprobante no corresponde con el ID del certificado del contribuyente o del PSE al que está afiliado".
  - orientacion.sunat.gob.pe "02. Certificados Digitales" says issuers must use certificates from providers listed in the
    INDECOPI **ROPS** (Registro Oficial de Prestadores de Servicios de Certificación Digital). No resolution is cited there.
  - Requirement for xhandler-service: (a) for each tenant, keep the certificate(s) the tenant registered in SOL, with
    validity dates. Validate before signing that the certificate subject contains the issuer's RUC and that the
    certificate is current on the issue date (SUNAT errors 2325 and 2326 check the SOL-registered list and its fec_baja).
    (b) In PSE mode, sign with the PSE's own registered certificate (see §2). (c) Warn when a certificate
    is uploaded that is not from a ROPS-listed CA.
- Deadlines to send to SUNAT (SEE-Del contribuyente). RS 097-2012 art. 12.1 was replaced by RS 000003-2023 art. 3, in force the day after publication (January 2023):
  factura and its notas, issue date + up to **3 calendar days**; DAE, recibo SP and their notas, 7 days; liquidación de compra,
  the next day. The same 3-day limit applies to the OSE (RS 117-2017 15.1 a, also by RS 000003-2023). This supersedes the 1-day rule
  of RS 193-2020. Current.
  - Requirement for xhandler-service: compute the deadline per document type and alert or block late submissions.
    A late factura is legally not a factura (RS 117-2017 15.1 last para.).
- Direct submission credentials (SUNAT web service). Annex B §4.2 of RS 117-2017 (as replaced by RS 114-2019 Annex XIII)
  states, for the SUNAT service described there: "debe usar el protocolo de seguridad WS-Security, el modelo
  UsernameToken, y usar como credenciales su código de usuario y la Clave SOL".
  The equivalent clause for SEE-Del contribuyente is in RS 097-2012 Annex 6, which I **could not read** (scan).
  - Requirement for xhandler-service: store SOL credentials (RUC + usuario + clave) per tenant, encrypted. In PSE mode, use the
    PSE's own SOL user (see §2).
- **SOL secondary users.** orientacion.sunat.gob.pe "07. Creación de Usuario Secundarios de la Clave SOL" says
  the principal SOL user creates secondary users under "Administración Usuarios → Crear Usuario → Asignar Perfiles".
  Actions taken by them "se considerarán realizados por tu persona". **No regulation is cited there, and no resolution I read
  regulates which profile is needed for CPE web services.** RS 109-2000 (the SOL regulation) was not read.
  - Requirement for xhandler-service: let tenants supply a secondary SOL user rather than the principal one, and
    document that the tenant stays liable for its use. Profile names are unverified.

---

## 2. PSE: RS 199-2015 and amendments (notably RS 108-2022)

### 2.1 What a PSE does ("actividades inherentes")
- RS 199-2015 art. 1. Creates the Registro de Proveedores de Servicios Electrónicos. Unregistered third parties performing
  the art. 3 activities "no pueden acceder al mismo". Current; RS 276-2017 added a 3rd paragraph for PSE-ME.
- RS 199-2015 art. 3 ("ACTIVIDADES INHERENTES A LA MODALIDAD DE EMISIÓN ELECTRÓNICA"). An issuer that uses third parties
  for these activities may only use registered PSEs:
  a) issuing the factura, boleta and their notas;
  b) sending a copy of the factura and its notas to SUNAT;
  c) generating and sending the comunicación de baja, the resumen diario "y de otro(s) formato(s) digital(es) señalados en el artículo 4° de
     la RS 300-2014" (wording of RS 113-2018, 4th modifying provision);
  d) receiving the CDRs sent by SUNAT.
  - **2nd paragraph:** "Las actividades detalladas en el párrafo anterior son únicamente aquellas que los referidos terceros realizan
    -en nombre del emisor electrónico- **utilizando su propio certificado digital y/o código de usuario y clave SOL**".
  - **3rd paragraph (liability):** under art. 4-A of DL 25632, "el emisor electrónico mantiene la responsabilidad respecto de las
    actividades detalladas".
  - For CRE/CPE, the same structure is set by RS 274-2015 art. 2.2 b), as amended by RS 117-2017 4th modifying provision (4.2): emission,
    sending, receiving CDRs, and reversal summaries, "utilizando su propio certificado digital y/o código de usuario y Clave SOL".
  - The GRE list (RS 255-2015) is unverified (scan). RS 108-2022 repealed para. 3.3 of RS 255-2015's 3rd modifying provision, whose
    content I could not read.
- RS 117-2017, 2nd final complementary provision. PSE registration also covers SEE-OSE. In SEE-OSE "los envíos a que se refiere
  el artículo 3 de esa resolución se deben realizar al OSE" (2.1.1). The CDR-rejection quotas of RS 199-2015 art. 7 e)/f) do not
  apply to CDRs issued in SEE-OSE (2.1.3).
  - Requirement for xhandler-service (PSE role): the PSE pipeline is: generate XML, **sign with the PSE's own certificate**, ZIP,
    submit (to SUNAT with the PSE's own SOL user, or to the issuer's OSE), receive the CDR and store it for the issuer.
    The tenant (issuer) remains legally liable, so contracts and the UI must say so.

### 2.2 Registration (Registro de PSE)
- Admission (RS 199-2015 art. 4): the application is filed through SOL with usuario and clave SOL. Conditions: domicilio "habido"; not suspended or
  deregistered; general income-tax regime **or Régimen MYPE Tributario** (art. 4 c, as amended by RS 117-2017 7th modifying provision);
  monthly returns for the last 6 months filed (12 months if previously removed); sales or income declared; and **"Tener la calidad de emisor
  electrónico del SEE"**. Validated online. Current.
- Registration (RS 199-2015 art. 5, as amended):
  - a) keep the art. 4 a–c conditions;
  - b–c) returns filed, with sales or income declared;
  - **d) "Registrar el certificado digital que utilizará para realizar las actividades señaladas en el artículo 3"** through SOL
    (wording of RS 108-2022);
  - **e) homologation: REPEALED by RS 108-2022** (1st repealing provision; in force the day after publication in June 2022);
  - f) no current conviction for a tax or customs crime;
  - g) capital or net assets of **150 UIT or more**. Start-ups file a sworn statement of net assets within 3 business days, in person or through MPV-SUNAT
    (RS 108-2022);
  - h) **5 or more employees** declared in the latest PLAME;
  - **i) ISO/IEC 27001 certification**: the certificate must be filed within 3 business days of the application. Required for registration since
    **2021-07-01** (RS 108-2022 wording; the dates moved through RS 309-2018, 277-2019, 105-2020 and 221-2020);
  - SUNAT rules **within 5 business days** (RS 108-2022). Silence means denial (silencio negativo).
- Obligations (RS 199-2015 art. 6): a) serve issuers in line with the norms; b) run a support platform that is the "primer nivel de asistencia técnica y
  funcional"; c) hold ISO/IEC 27001 (for registrants up to 2021-06-30, from 2021-07-01, per RS 221-2020; RS 077-2021 gave a grace period to
  2021-09-30 for those who had started certification); d) allow SUNAT inspections. SUNAT may request documentation. Current.
- Conditions to remain registered (RS 199-2015 art. 7): keep art. 4 a–c and art. 5 f–i; file returns; declare sales; meet art. 6;
  rejected CDRs per issuer (e) and in aggregate (f) **≤10% in the first 12 months, then ≤5%** (2% for re-entrants during 12 months).
  Checked **semi-annually**. Under RS 117-2017 2nd FCP 2.1.3, these quotas do not count CDRs issued in SEE-OSE.
- Removal (RS 199-2015 art. 8): failing any art. 7 condition leads to removal, notified through the buzón. A removed PSE may keep serving for **90 calendar days**
  (RS 108-2022; previously 30). SUNAT notifies affected issuers and publishes the list. Current.
  - Requirement for xhandler-service: track ISO 27001 validity, the rejection ratio per issuer and in aggregate per month
    (dashboard with a 5% alarm), the 150 UIT and 5-employee conditions, and a 90-day offboarding playbook.

### 2.3 How the issuer authorizes the PSE
- SEE-Del contribuyente: RS 097-2012 art. 6.3 b) (RS 287-2017; selection in SOL) and art. 6-B (RS 199-2015, as amended by RS 117-2017):
  the issuer registers the PSE "en la opción habilitada para tal efecto en SUNAT Operaciones en Línea". Revocation is also done in SOL, and
  "los proveedores ... solo pueden seguir realizando las actividades ... hasta el sétimo día calendario del mes siguiente".
  - The original RS 199-2015 also required the **issuer** to register the certificate(s) the PSE would use (097-2012 art. 6.2, 2nd para., and
    the old 6-A e.ii and 6-B). RS 287-2017 replaced arts. 6 and 6-A without that clause, and the RS 117-2017 wording of 6-B drops it. Under the
    current text the **PSE registers its own certificate** (RS 199-2015 art. 5 d). In practice SUNAT's OSE list "Listado de certificados del emisor"
    and error 2325 link the certificate to the contributor or "PSE al que está afiliado".
- SEE-OSE: RS 117-2017 art. 9.1.3 b) (Modalidad II), art. 11.2 (revocation effective until the 7th day of the following month) and art. 11.3
  (authorization effective the next calendar day).
- SUNAT exposes the relation to OSEs in "Listado de contribuyentes asociados a los emisores" (num_ruc, num_ruc_asociado,
  ind_tip_asociacion 1=PSE / 2=OSE, fec_inicio, fec_fin) in AjustesValidacionesCPE_Listados.md. Errors 0111 ("no es emisor ni PSE") and 0154,
  and CDR-OSE rule 2873, treat the PSE relation as valid until the 7th calendar day of the month after fec_fin.
  - Requirement for xhandler-service: (a) onboarding checklist: "register us as PSE in SOL". (b) Before each submission, check the PSE
    authorization is effective on that date: from the next day after registration, and until the 7th of the month after revocation.
    (c) Handle errors 0111, 0154, 2325 and 2873 with clear messages.

### 2.4 Credentials used toward SUNAT
- RS 199-2015 art. 3, 2nd para. (quoted above): the PSE uses **its own certificate and/or its own código de usuario and clave SOL**.
  Confirmed by the error 0154 description: "El usuario que invoca el servicio es diferente al RUC del archivo (emisor) y no existe
  relación vigente entre el usuario que invoca el servicio y el RUC del archivo (relación PSE)".
  - Requirement for xhandler-service: in PSE mode, the SOAP UsernameToken is **the PSE's RUC + SOL user**, while the ZIP and XML are named with
    the **issuer's RUC**. The signature uses the **PSE's** certificate. The cac:Signature/SignatoryParty ID may be the issuer's RUC or the
    sender's RUC (error 2078: "igual al RUC del emisor o al RUC que se envía el comprobante").

### 2.5 Liability and sanctions
- Issuer liability: RS 199-2015 recitals and art. 3, last paragraph (DL 25632 art. 4-A). PSE consequence: removal only (art. 8). I found no PSE fines in
  the resolutions read.

---

## 3. OSE: RS 117-2017 and all amendments found

### 3.1 Definition and scope
- RS 117-2017 art. 1.24: the OSE is "Al sujeto inscrito en el Registro OSE que comprueba informáticamente el cumplimiento de los aspectos
  esenciales de lo emitido en el SEE – OSE". Art. 4: OSE register, based on DL 1314 sole article. The OSE checks the documents of the issuers that
  **contract it**.
- Documents (art. 3, as amended by RS 123-2022): factura, boleta, DAE and notas (3.1); CRE/CPE (3.2); resumen diario and reversal summaries (3.3);
  comunicación de baja and reversals (3.4).

### 3.2 Admission requirements (art. 5.1)
a) legal entity; b) RUC active; c) domicilio "habido"; d) general income-tax regime; e) electronic issuer in the SEE for factura **and/or** boleta (wording of RS 287-2017);
f) returns filed for 6 months (12 if previously removed); g) no coactive collection proceedings;
**h) "Haber registrado previamente, en SUNAT Operaciones en Línea, uno o más certificados digitales que utilizará de manera exclusiva en su rol de OSE ...
Ese(esos) certificado(s) deben tener 2048 bits"**. Filed through SOL, validated automatically (5.2). Current.
- Requirement for xhandler-service: keep a **dedicated OSE signing key** (2048-bit RSA or more), separate from any PSE or tenant key, registered in SOL.

### 3.3 Registration requirements (art. 6.1, as amended)
- a) keep the art. 5.1 conditions (except f, h); b) returns filed; c) not under bankruptcy restructuring and not barred from public contracting;
  d) no legal representative with a firm conviction for an intentional crime; e) no legal representative under coactive collection.
- f) **capital or net assets of 300 UIT or more** (SUNARP capital or box 390 of the annual return).
- g) **carta fianza** per Annex D §I: issued to SUNAT by an SBS-authorized bank or insurer, **28 UIT**, irrevocable and immediately enforceable.
  Validity of at least 12 months in the original; RS 287-2017 changed it to 10 months for the initial application (6 months if reused) and 12 months on
  renewal. It must be renewed within 45 days before expiry (Annex D §II).
- h) **information-security controls of Annex A**, proven by an audit report from a firm experienced in ISMS implementations, "firmado por un Lead Auditor ISO certificado".
- i) **SUNAT test process** (connectivity plus functional tests) using the art. 5.1 h certificate, within **25 business days**.
  orientacion.sunat.gob.pe "07. Referente al proceso de pruebas para ser OSE" adds: connectivity and IP configuration on business days 3–10, functional tests
  on days 3–27, and 8-day iterations.
- j) sworn statement on whether the OSE commits to **"servicio a nivel nacional"** (Annex E; added by RS 092-2018). Definition 1.41 (RS 092-2018):
  service to any issuer "previa contratación y en cualquier lugar ... siempre que exista conexión a la Internet".
- k) **no ex-SUNAT staff** (or their relatives up to the 4th degree of consanguinity or 2nd of affinity) who worked in e-invoicing design, norms or IT within the last 24 months,
  as legal representative, attorney-in-fact, board member, or holder of 30% or more of capital (added by RS 095-2019).
- Documents for g), h) and j) are filed within 20 business days (6.2, as replaced by RS 092-2018). SUNAT rules within **30 business days**; silence means denial (6.4).
- Requirement for xhandler-service: the OSE launch plan needs a legal entity with 300 UIT or more of capital, a 28 UIT bond, an Annex A audit by an ISO
  Lead Auditor, a 25-business-day SUNAT test window with static egress IPs, the Annex E declaration, and a staff screening process for 6.1 k.

### 3.4 Obligations (art. 7, as amended)
| # | Obligation | Source / status |
|---|---|---|
| 7.1 | Keep meeting the conditions. **Pass new SUNAT tests** whenever a new document type or new requirements are introduced, "a solicitud de la SUNAT" | 2nd–3rd paras added by RS 340-2017 |
| 7.2 | File monthly returns | original |
| 7.3 | Maintain the carta fianza | original |
| 7.4 | **"de forma inmediata"** check the issuance conditions of what contracting issuers send, and issue the CDR or the Comunicación de Inconsistencias | original |
| 7.5 | Send each CDR **and** the validated document to SUNAT **within 1 hour** of the check, per Annex C. SUNAT returns an **acuse de recibo** | RS 114-2019 (acuse added) |
| 7.6 | **≥99.96 % yearly availability** of the reception web services (fine note (6) of 8.1.2) | original |
| 7.7 | **ISO/IEC 27001 certificate from the start of the 2nd year**, then a yearly audit report showing the ISMS is maintained, each filed within 30 calendar days of the year start, from an accredited body | original |
| 7.8 | Allow SUNAT to verify the platform against Annex C, Annex A and ISO 27001 | original |
| 7.9 | Offer issuers **secure web-service reception channels** per Annex B, with authentication "con usuario y contraseña" | original |
| 7.10 | Keep **tax secrecy** (DL 1314 sole article, numeral 3) | original |
| 7.11 | Keep confidential other information SUNAT provides (lists, padrones) | original |
| 7.12 | **Keep for 1 month** each document that received a CDR, plus all CDRs and inconsistency notices. SUNAT may once request their resend "en la forma y plazo que la SUNAT señale" | RS 114-2019 |
| 7.13 | Other obligations in the resolution | original |
| 7.14 | Honor the national-service commitment if declared. SUNAT publishes each OSE's scope and may request evidence | RS 092-2018 |
| 7.15 | Public **web query** during the 7.12 period: validity of factura, DAE and notas (CDR or inconsistency), and for boletas whether issued and when | RS 242-2018 (from 2019-01-01); GRE removed by RS 123-2022 |
| 7.16 | On its own invoices, list the **comprobación informática service as a separate line item** with its price and pricing basis (per document, per period, ...) | RS 242-2018 |
| 7.17 | In any advertising where the validation service is offered with other services, **identify it distinctly** | RS 242-2018 |
| 7.18 | Ongoing ex-SUNAT staff bar (as in 6.1 k) | RS 095-2019 |

Requirements for xhandler-service (OSE role):
- Synchronous validation. sendBill must return a signed CDR-OSE or a fault ("inmediata"). sendSummary and getStatus are asynchronous with a ticket (Annex B §1).
- An outbound queue to SUNAT with a **1-hour SLA** per document, measured from the CDR's ResponseDate/Time. SUNAT checks this as observation 4196 ("mayor a 1 hora").
  Handle the acuse de recibo, retries, and batches (sendPack, async, ticket + getStatus; one CDR-OSE per XML in the batch; duplicates rejected).
- HA design for **99.96 %** (about 3.5 h of downtime per year), with monitoring evidence.
- Retention of at least 1 month of XML, CDRs and inconsistencies, kept longer for the tenant's own needs, plus a bulk re-send capability for SUNAT requests.
- A public validity-query web page (7.15).
- Billing: separate invoice lines for "servicio de comprobación informática" (7.16). Marketing pages must name it separately (7.17).
- ISMS: ISO 27001 by year 2 and yearly audits. Annex A controls (selected from ISO/IEC 27002:2013) include, from the "Aspectos complementarios":
  **HSM** for cryptographic controls (FIPS 140-2 Level 2 + CC EAL4 if dedicated; **Level 3** + EAL4 if shared), **OWASP** secure development, ISO/IEC 15288 and
  12207 processes, an incident register, **pentests / ethical hacking**, clock synchronization (12.4.4), and separate dev/test/prod environments (12.1.4).
  So the **OSE CDR signing key should live in an HSM** (cloud HSM must meet FIPS 140-2 L3 if shared).

### 3.5 CDR issuance and signing (Annex C, current text = RS 114-2019 Annex XIII)
- The CDR-OSE is a UBL 2.1 ApplicationResponse, CustomizationID 1.0. Fields: UUID ID; IssueDate/Time = OSE reception; ResponseDate/Time = check time;
  SenderParty = RUC of the sender (issuer **or PSE**, "si el RUC es de un PSE, éste debe estar autorizado por el emisor (vinculado) a la fecha de comprobación");
  ReceiverParty = **OSE RUC**: "El certificado digital con el que se firma el CDR-OSE, debe corresponder a este RUC", registered in the padrón and
  linked to the issuer on the check date. ResponseCode is fixed at "0" (accepted). There are optional StatusReasonCode observations,
  a DocumentReference with series-number, date, time, type and hash, plus issuer and recipient IDs.
- The local file `AjustesValidacionesCPE_CDR-OSE-Comprobante.md` lists the SUNAT-side validations of the CDR-OSE, including
  2824 (CDR signing certificate ≠ OSE RUC), 2825 (OSE not in padrón), 2874 (OSE not linked to the issuer; relation valid until the 7th of the following month),
  2873 (PSE not linked), 4196 (more than 1 h), 2950 (out-of-time submission) and 2804/2809/2810 (date coherence).
  `AjustesValidacionesCPE_CDR-OSE-Resumen.md` covers summaries.
- Inconsistency notice: minimum `<faultstring>` (code a4) + `<detail>` (an..100) (Annex C §II).
- OSE↔SUNAT connectivity (Annex C §IV): over the Internet, **restricted to IPs declared by the OSE**, domains e-ose1/e-ose2, e-descargaose1/2 and
  e-betaose1/2. "Es responsabilidad del OSE el balanceo entre ambos dominios". Methods: SendBill, SendSummary, SendPack, GetStatus. The XML signature must be the
  issuer's or the authorized PSE's; the CDR-OSE signature must be the selected OSE's.
- SUNAT lists to OSEs (Listados sheet): contributors and padrones, associations (PSE/OSE), issuer certificates, issued CPE, physical
  authorizations, contingency ranges, branches, and parameters (exchange rate, perception and retention regimes). These are confidential (7.11).
- Requirement for xhandler-service: implement the full rule set of AjustesValidacionesCPE (codes 0100–4xxx). Sync SUNAT lists daily. Verify the issuer certificate
  against the SUNAT list (2325/2326) and the PSE link (2873). Produce CDR-OSE exactly per Cuadro 1, signed with the OSE key. Use static egress IPs and dual-domain
  load balancing toward SUNAT. Support SUNAT beta (e-betaose).

### 3.6 Issuer side: choosing an OSE and deadlines
- By choice (art. 9.1): opt in through SOL, meet the RUC conditions, and choose **Modalidad I** (own certificate registered + e-mail) or **Modalidad II** (choose PSEs +
  e-mail; also a certificate if the issuer will sign itself).
- **9.2 and art. 10:** before issuing, the issuer "debe autorizar, a través de SUNAT Operaciones en Línea, al o a los OSE que contrató, mediante el registro respectivo.
  La autorización surte efecto desde el día calendario siguiente".
- **Art. 11.1:** more OSEs can be added at any time (effective the next day). **Revocation requires keeping or adding another OSE** and takes effect from the
  **7th calendar day of the month after** it is registered.
- Deadlines to send to the OSE: factura and its notas, issue date + **3 calendar days** (15.1 a, RS 000003-2023); DAE and notas, 7 days (15.1 b);
  boleta individually up to the **5th calendar day**, otherwise by resumen diario (15.2, RS 114-2019); resumen diario within 7 days (23.1, RS 114-2019);
  baja within 7 days of the CDR (17.4).
- Effects (12.5): SUNAT, not the issuer, stores and keeps what is issued through SEE-OSE, except boletas reported by summary.
- Requirement for xhandler-service: per company, store the authorized OSE(s) and their effective dates. Our own OSE must reject a sender whose issuer has not
  authorized it (2874 logic). Outbound to third-party OSEs needs an adapter per OSE (Annex B SendBill / SendSummary / GetStatus over SSL, with OSE-defined user and password).

### 3.7 Sanctions (art. 8, as replaced by RS 191-2019, in force 2019-11-01; DL 1380 applies the Código Tributario)
- **Removal from the register:** very serious, 3 years: breach of tax secrecy; legal representative convicted. Serious, 1 year: no carta fianza; ex-SUNAT staff
  (7.18); breach of the national-service commitment; breach of confidentiality of SUNAT information.
- **Fines (8.1.2):** 25 UIT each for: not validating the documents it **itself issues** in this system (b); not validating immediately or not issuing a CDR or
  inconsistency notice (c, per document); not keeping documents (e, per document); not meeting 99.96 % (f); no 7.15 query (d, RS 242-2018).
  10 UIT for not sending to SUNAT within 1 hour (g, per document). 1 UIT for 7.16 and 7.17 breaches.
  Graduated discounts by frequency: 90 % / 75 % / 50 % / none (8.1.4).
- **Removal or fine with cure (8.1.3):** ISO 27001 non-compliance (3 years or 25 UIT); certificate or audit not filed (1 year or 10 UIT); verification
  refused (1 year or 10 UIT); no secure reception channels (1 year or 25 UIT). The fine applies only if cured and paid within 15 business days.
- Removal takes effect 3 months after the decision is final (8.1.6). Sanctioned OSEs are published (8.2).
- Requirement for xhandler-service: per-document audit evidence (receipt time, check time, SUNAT send time, acuse), because fines are counted **per document**.

### 3.8 Contingency
- No OSE-specific contingency article was found in RS 117-2017 or its amendments. Availability is governed by 7.6 (99.96 %), and Annex A requires
  continuity controls (§17.1). Issuer-side contingency is the RS 300-2014 art. 4 regime: printed or imported documents when issuance is impossible, reported
  later in a "declaración jurada informativa por contingencia". This declaration is a document type the OSE receives and forwards (Annex B and C current text,
  RS 114-2019; the file names use a numeric series).
  - Requirement for xhandler-service: accept and forward contingency declarations (RF / numeric series) in OSE mode. Generate them in issuer mode.

---

## 4. One company as both PSE and OSE

- **No provision found that forbids holding both registrations.** The PSE register (RS 199-2015) and the OSE register (RS 117-2017) have separate, independent
  conditions. RS 117-2017 defines the PSE as able to serve "en el SEE – Del contribuyente o en el SEE – OSE" (1.25), and the CDR-OSE structure simply records
  the sender (issuer or PSE) and the OSE as distinct fields. **I did not find an explicit statement allowing it either.** SUNAT's FAQ pages
  (cpe.sunat.gob.pe) returned 403 and could not be checked.
- Constraints that do apply to a combined entity:
  1. **Separate OSE certificate:** RS 117-2017 art. 5.1 h requires certificates used "de manera exclusiva en su rol de OSE". The CDR-OSE key cannot be the
     PSE signing key (RS 199-2015 art. 5 d).
  2. **Service separation in billing and marketing:** 7.16 and 7.17 (RS 242-2018) require the validation service to be invoiced as a separate item and
     identified distinctly when offered with other services, such as PSE services.
  3. The issuer must authorize the company **twice** in SOL: once as PSE (Modalidad II / art. 11.3) and once as OSE (art. 9.2/10). SUNAT stores these as
     separate associations (ind_tip_asociacion 1 vs 2).
  4. **Self-validation is explicitly contemplated for the OSE's own documents:** fine 8.1.2 b) applies to "No realizar ... la comprobación informática de las
     condiciones de emisión de los documentos que él mismo emita en este sistema". As issuer the OSE must validate its own invoices like any other.
     There is **no rule forbidding an OSE from validating documents it produced as PSE** for a third-party issuer, but the full SUNAT rule set and the 7.4
     duty still apply. Validation must not be weakened because the document came from the same company.
  5. PSE rejection-ratio limits (RS 199-2015 art. 7 e/f) do not count CDRs issued in SEE-OSE (RS 117-2017 2nd FCP 2.1.3). So a PSE whose documents go
     through an OSE, including itself, is not measured on OSE rejections.
- Requirement for xhandler-service: (a) separate keys and HSM partitions for PSE signing and OSE CDR signing. (b) Separate billing SKUs and a distinct
  public description of the OSE service. (c) OSE validation runs as an independent component with no bypass for PSE-originated traffic. Log sender vs issuer
  to show this. (d) Onboarding asks the issuer for both SOL authorizations. (e) Obtain written confirmation from SUNAT (consulta) before marketing a combined
  PSE+OSE offer, given the absence of an explicit rule.

---

## Unverified / could not access

- **RS 097-2012 and RS 300-2014 original texts** (image scans, no OCR). These include the definition of "certificado digital" (097-2012 art. 2.12), Annex 6
  (SEE-Del contribuyente web service and credentials), art. 4 of 300-2014 (contingency regime details) and art. 2 (incorporation). Only quoted or replaced
  fragments were verified.
- **RS 255-2015 (GRE) and RS 274-2015 (CRE/CPE)**: scans. Their PSE-activity lists were not read, except the CRE/CPE text re-enacted by RS 117-2017 4th
  modifying provision.
- **RS 109-2000 (SOL)**: not read. No regulation found on secondary-user profiles needed for CPE web services. Only the orientación walkthrough was read.
- **Annex E (national-service declaration, RS 092-2018 annex)** and the annexes of RS 340-2017 (IX, X), RS 113-2018 (annex C) and RS 013-2019 (V, VI)
  were not read. I relied on the latest full Annex B/C replacement found (RS 114-2019 Annex XIII). Later partial annex changes, if any, are unverified.
- **DL 1314, DL 1380, DL 25632 art. 4-A, DS 052-2008-PCM**: cited only through the resolutions' text. Not read directly.
- **2023–2026 indexes:** only RS 000003-2023 touched OSE deadlines. RS 000048-2026, 000075-2026, 000108-2026 and 000143-2026 were scanned for art. 7/8
  changes to RS 117-2017 and none were found, but they were not read in full. No 2023–2026 amendment to RS 199-2015 was found in the index titles.
  Amendments whose titles do not mention OSE/PSE could have been missed.
- **Explicit SUNAT position on PSE+OSE in the same company**: not found (cpe.sunat.gob.pe returns 403; gob.pe returns 418). The conclusion in §4 is an
  inference from the absence of a prohibition, plus the 5.1 h, 7.16/7.17 and 8.1.2 b) provisions.
- **Exact SUNAT web-service URLs and the current e-ose host names** (Annex C §IV gives only short names) and the current test-process details beyond the
  orientación page.
- PSE fines: none found. Whether PSE breaches can be sanctioned other than by removal is unverified.
