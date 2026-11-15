# xhandler-service: non-tax legal requirements in Peru (with tax-on-the-SaaS and data residency notes)

Research date: 2026-11-13. Every citation below was read in the source file listed, unless it is marked **UNVERIFIED**.
Source PDFs and extracted text are in `/tmp/sunat-regs/legal/`. Most were downloaded from cdn.www.gob.pe or www.sunat.gob.pe.
This is a regulatory reading for requirements engineering. It is not legal advice. Have Peruvian counsel validate it before launch.

Sources read (file -> norm):
- `ley29733b.pdf`: Ley 29733, Ley de Protección de Datos Personales. Compiled text from gob.pe/ANPD that includes the DL 1353 (2017) amendments. The file is versioned May 2023. I did not check whether there are later amendments.
- `ds016-2024-jus.pdf`: DS 016-2024-JUS, the new Reglamento of Ley 29733. Published in El Peruano on 30-Nov-2024. It repeals DS 003-2013-JUS.
- `du007-2020.pdf`: DU 007-2020, Marco de Confianza Digital (El Peruano, 09-Jan-2020).
- `ds126-2025-reglamento.pdf`: Reglamento of DU 007-2020, approved by DS 126-2025-PCM. The gob.pe page dates it 04-Nov-2025. The DS cover PDF `ds126-2025-pcm.pdf` is image-only and could not be read.
- `dl1412.pdf`: DL 1412, Ley de Gobierno Digital. `ds029-2021-pcm.pdf`: its Reglamento (DS 029-2021-PCM, 19-Feb-2021), which also amends DS 052-2008-PCM.
- `ds052-2008-pcm.pdf`: Reglamento of Ley 27269 (firmas y certificados digitales). This is the original 2008 text. The 2021 amendments were read inside DS 029-2021-PCM.
- `res118-2021-cfe.pdf`, `res175-2024-dgi.pdf`: INDECOPI IOFE resolutions on remote signing (firma remota). `res175-2025-dgi.pdf` was downloaded but only its title was used.
- `cpdc-29571.pdf`: Código de Protección y Defensa del Consumidor, Ley 29571. Editora Perú updated text dated 12/02/2026, including DL 1729 (Feb 2026) and Ley 32495 (Nov 2025).
- `ds011-2011-pcm.pdf`: Reglamento del Libro de Reclamaciones, DS 011-2011-PCM. This appears to be the original text. Later amendments are not consolidated in it.
- SUNAT: TUO Ley del IGV, Cap. I, III and XII (`igv_cap1.pdf`, `igv_cap3.pdf`, `igv_cap12.pdf`); TUO LIR Cap. II (`lir_capii.pdf`); Código Tributario Libro II (`ct_libro2.pdf`); RS 117-2017/SUNAT (`/tmp/sunat-regs/117-2017.pdf`, already present).
- ANPD procedure page "Inscribir banco de datos" (gob.pe/8060, last changed 22-May-2026).

---

## 1. Personal data protection (Ley 29733 + DS 016-2024-JUS)

### 1.1 Entry into force and scope
- DS 016-2024-JUS, 1st Disposición Complementaria Final: the Reglamento enters into force **120 calendar days after publication**. The ANPD procedure page says it has been in force **"desde el 31 de marzo de 2025"**.
- Reglamento Art. IV.1: it applies to processing (tratamiento) of personal data **even when the data is not in a banco de datos**.
- Reglamento Art. VI (territorial scope):
  - Art. VI.2: it applies to processing "efectuado por un **encargado del tratamiento, con independencia de su ubicación**, a nombre de un titular de banco de datos personales establecido en territorio peruano".
  - Art. VI.3.1: it also applies to controllers outside Peru that offer goods or services to data subjects in Peru.
- Reglamento Art. VII: a controller (responsable) outside Peru that falls under VI.3 or VI.4 must designate a representative in or for Peru. The representative can be named in the privacy policy or communicated to the ANPD.
- Requirement for xhandler-service: Peruvian law applies whether the operating company or the cluster is in Peru or abroad. If the operator is a foreign company acting as controller for its own customer/billing data, it must designate a Peruvian representative and publish that person in its privacy policy (Art. VII.2).

### 1.2 Roles: SaaS as encargado (processor) vs responsable (controller)
- Reglamento Art. III.10 defines the *encargado* as the party that processes data "por cuenta u orden del responsable". Art. III.22 defines the *responsable* as the party that "decide sobre la finalidad y medios del tratamiento".
- **Invoice data** (end customers' DNI, name, address, email in CPE XML): the issuing company (the tenant) is the responsable. xhandler-service is its **encargado**.
- **Its own subscriber, user, reseller and billing data** (including what goes to Lago): xhandler-service is the **responsable**.
- Encargado obligations:
  - Ley 29733 Art. 30: data processed for third parties cannot be used for any other purpose, and cannot be transferred to others, "ni aun para su conservación". After the service ends the data must be deleted, unless the client expressly authorises retention.
  - Reglamento Art. 31.1: no transfers to third parties without the responsable's authorisation.
  - Reglamento Art. 31.2: retention after the service ends is **at most 2 years from the end of the last encargo**. When a legal retention duty exists, the data "deben ser devueltos al responsable". Indefinite retention is prohibited.
- Outsourced technology (Reglamento Arts. 28-30), which applies to automated SaaS processing:
  - Art. 29: inform data subjects of subcontracting; never claim ownership of the banks; guarantee confidentiality, integrity and availability (CIA); leave control with the responsable; "garantizar y evidenciar la destrucción" of the data after the service.
  - Art. 30 (prestador obligations): communicate changes to the privacy policy or service conditions; let the responsable limit the processing; keep adequate security; "garantizar y evidenciar la supresión" once the service ends and the responsable "los haya podido recuperar"; block unauthorised access and inform the responsable of requests from authorities.
- Subcontracting (Reglamento Arts. 32-33): sub-processors (cloud provider, email provider, resellers if they touch data) need the responsable's prior authorisation. The authorisation can be given in the main contract. The burden of proving it is on the encargado. A sub-processor that uses the data for other purposes becomes liable as a titular.
- Requirement for xhandler-service:
  - (a) A Data Processing Agreement in the tenant ToS that covers purpose limitation, the list of sub-processors and how changes are notified (Art. 30.1), instructions, the security annex, and authority-request notification.
  - (b) Tenant self-service export, then provable deletion when the contract ends (Arts. 29.5, 30.4). Keep a deletion certificate or log as evidence.
  - (c) Default post-termination retention of ≤ 2 years (Art. 31.2).
  - (d) Never use tenant invoice data for xhandler-service's own purposes (analytics, marketing) unless anonymised.
  - (e) Resellers or partners who access tenant data must be bound as authorised sub-processors.
- Tension to resolve with counsel: tax law requires 5-year conservation of CPE (see §6). Under Art. 31.2 the encargado should *return* the data to the responsable rather than keep it. Design: hand back the XML/CDR archive at termination, or offer a paid archive contract in which the tenant expressly authorises retention (Ley Art. 30, second paragraph).

### 1.3 Legal bases
- Ley 29733 Art. 13.5: consent is the rule ("previo, informado, expreso e inequívoco").
- Ley 29733 Art. 14 (as amended by DL 1353) lists the exceptions:
  - no. 5: data needed for "la preparación, celebración y ejecución de una relación contractual en la que el titular de datos personales sea parte";
  - no. 10: "…otros que respondan a un mandato legal";
  - no. 13: other powers established by law.
- Reglamento Art. 13: every transfer must be informed to the data subject (Ley Art. 18).
- Requirement for xhandler-service:
  - The tenant (as controller) relies on contract and legal mandate (the CPE obligation) for the buyer data in invoices. The service needs no separate consent from end customers for those.
  - For its own processing: use contract (Art. 14.5) for customer accounts and billing. Get separate, unbundled consent for marketing.
  - Publish a privacy policy meeting Ley Art. 18 and Reglamento Art. 7 (not read in detail). Incomplete information is an infraction: ≤ 2 missing conditions is leve (Art. 132.5); ≥ 3 is grave (Art. 133.2).

### 1.4 Registration of banks of personal data (Registro Nacional de Protección de Datos Personales)
- Reglamento Art. 42.1: whoever creates, modifies or cancels a banco de datos personales must register it.
- Reglamento Art. 43.3: registration is free.
- Reglamento Art. 44.1: registration must be kept up to date.
- Reglamento Art. 45: registration is a form filed with the Dirección de Protección de Datos Personales, with automatic approval.
- The ANPD page says the filing is online via the SIPDP and gives a constancia and a bank code.
- Ley Art. 34 (DL 1353 text): the Registry also records "las comunicaciones de flujo transfronterizo de datos personales".
- Requirement for xhandler-service: register its own banks (clients/users, billing, support/complaints, and employees if any). The ANPD page puts the obligation on the *titular* of the bank. Whether an encargado must register the tenant invoice data as its own bank is **not stated** in the articles read; the expected answer is no, because the tenant is the titular. Tenants should be told to register their customer bank and to name xhandler-service as encargado. Ley Art. 29 says the registry publicises "de ser el caso, de su encargado". Failing to register or update is leve (Art. 132.4), and grave if it persists after the ANPD requests it (Art. 133.13).

### 1.5 Cross-border transfer (flujo transfronterizo)
- Reglamento Art. III.15 defines it as transfer to a recipient in another country "sin importar el soporte… ni el tratamiento que reciban". Hosting on a cloud outside Peru is therefore a cross-border flow.
- Ley 29733 Art. 15 (DL 1353 text):
  - Transfers are allowed only to countries with an adequate level of protection.
  - Otherwise the emisor must guarantee processing in line with the law.
  - Exceptions include no. 4: data needed to perform a contract with the data subject, "incluyendo lo necesario para… soporte del servicio… facturación…".
  - Exception no. 7: consent.
- Reglamento Art. 18: adequacy is determined by the Dirección General by resolution (Art. 19). Otherwise use "cláusulas contractuales modelo u otros instrumentos jurídicos" (Art. 20.1). The Dirección General issues model clauses (Art. 20.2).
- Reglamento **Art. 21.2**: "**En cualquier caso**, el flujo transfronterizo de datos personales debe ponerse en conocimiento" of the Dirección General, using the approved form. It is then inscribed in the Registro Nacional.
- Reglamento Art. 21.1: a voluntary opinion can be requested. The ANPD answers within 30 days.
- Failing to communicate the flow is a leve infraction (Art. 132.8).
- Requirement for xhandler-service:
  - (a) If Kubernetes, backups, logs, email or monitoring sit outside Peru, the flow must be communicated to the ANPD on the official form. The tenant, as responsable/exporter, must do this for invoice data. xhandler-service does it for its own banks. Supply tenants with a ready-made "flujo transfronterizo" disclosure: countries, recipients, sub-processors, purpose.
  - (b) Sign the ANPD model clauses (if published; **UNVERIFIED**) or equivalent DPA/SCC terms with the cloud provider and any foreign affiliate.
  - (c) Make hosting region configurable, ideally with a Peru or Peru-adjacent region option. Keep a sub-processor and country list.
  - (d) Disclose the transfer in privacy notices (Reglamento Art. 13.2).

### 1.6 Security measures
- Ley Art. 16: technical, organisational and legal measures are required. Processing in banks that lack them is prohibited.
- Reglamento **Art. 46** (digital platforms, SaaS, systems) must document and implement:
  1. Access control: user lifecycle management; identification and authentication; **privilege review at least every six months**; authentication mechanisms such as passwords, digital certificates or tokens.
  2. Monitoring and periodic review of controls, and role-based staff training.
  3. **Logs** of interactions with personal data: user account, login/logout, view/modify/delete/import/export. Logs must be legible and timely, have a disposal procedure, and be **kept at least 2 years**. Operator traceability logs must be continuous and available immediately.
  4. Controls that prevent unauthorised copying. Non-institutional messaging and email must be formally approved.
- Reglamento **Art. 47**: a formally approved **Documento de Seguridad with "fecha cierta"**, containing access and privilege management, internal data-lifecycle policies, and an **inventory of personal data and systems** (flagging sensitive data). It may follow NTP-ISO/IEC 27001.
- Reglamento Art. 48: secure areas, secure equipment, and secure, continuous backups with integrity checks. Reference: NTP-ISO/IEC 27001:2022.
- Reglamento Art. 50.5: wipe media before reuse.
- Reglamento **Art. 51**: **backups at least weekly** (unless the data did not change), with security over storage, transfer and destruction. Integrity must be verified, including full recovery.
- Reglamento Art. 52: data leaving the processing environment needs the titular's authorisation and protective measures such as encryption, digital signatures and checksums.
- Reglamento Art. 57: staff and contractors who do not need access must be technically restricted. Contracts must include a no-access and secrecy clause.
- Ley Art. 17: confidentiality binds the titular, the encargado and everyone involved, and survives the relationship. Breach is grave (Art. 133.11).
- Requirement for xhandler-service:
  - RBAC with semi-annual access reviews.
  - Immutable audit log of every personal-data read, write and export, by tenant users and by operators, kept ≥ 2 years.
  - Weekly-or-better backups with restore tests.
  - Encryption in transit and at rest.
  - A Documento de Seguridad with fecha cierta, plus a data inventory.
  - DLP and approved channels for staff.
  - Secrecy clauses with staff, contractors and resellers.

### 1.7 Breach notification
- Reglamento Art. III.16 defines a security incident as destruction, loss, unlawful alteration, or unauthorised communication or exposure.
- Reglamento **Art. 34.1**: the titular/responsable notifies the ANPD **within 48 hours** of knowing about an incident that:
  - exposes large volumes (by amount or type of data), or
  - may affect a large number of people, or
  - involves sensitive data, or
  - causes evident harm to other rights.

  A later notice must explain the delay. The duty remains even if the incident was solved internally.
- Art. 34.2 minimum content: nature of the incident, data types and approximate number of people affected; contact of the Oficial or other contact point; likely consequences; measures taken or proposed.
- Art. 34.3: data subjects affected in other rights must be told **within 48 hours**, in plain language. Art. 34.4: no duty to tell data subjects if there was no such harm and the incident was fully overcome.
- **Art. 34.5**: if the incident happens in or through the digital environment, it must **also be notified to the Centro Nacional de Seguridad Digital (CNSD)** for the Registro Nacional de Incidentes de Seguridad Digital under DU 007-2020.
- Art. 35: every incident must be documented (facts, effects, measures).
- **Art. 36**: the **encargado must inform the responsable "de forma inmediata"** of any incident it knows about.
- Not notifying the ANPD when required is grave (Art. 133.12).
- Requirement for xhandler-service:
  - An incident-response runbook. As encargado: notify affected tenants **immediately**, with the Art. 34.2 fields pre-filled, so they can meet their 48-hour deadline.
  - As responsable for its own data: notify the ANPD within 48 hours, plus the CNSD for digital incidents (see §3).
  - Keep an incident register (Art. 35).
  - The tenant notification channel and a template are needed in the product.

### 1.8 Oficial de Datos Personales (DPO)
- Reglamento **Art. 37.1**: the titular/responsable **and the encargado** must designate a DPO when:
  - (1) it is a public entity; or
  - (2) it processes **large volumes** of personal data (by amount or type), or the processing may affect many people, or involves sensitive data, or causes evident harm; or
  - (3) its core activity involves sensitive data.
- Art. 37.2: one DPO is allowed per corporate group.
- Art. 37.4: the DPO's contact details must be published visibly.
- **Art. 37.5**: the DPO must be communicated to the ANPD **within 15 days** of designation or change ("días" means working days under Art. III.8).
- Art. 38: the DPO needs proven expertise. The role may be part-time or held by someone outside the company.
- Art. 39: the DPO advises, monitors compliance, cooperates with the ANPD and is its contact point.
- Phased obligation for cases (2) and (3) (1st DCF), counted from publication on 30-Nov-2024. Dates below are computed from that:

  | Company size (annual sales) | Obligation applies from |
  |---|---|
  | > 2300 UIT | 1 year after publication (≈ 30-Nov-2025) |
  | 1700–2300 UIT | 2 years (≈ 30-Nov-2026) |
  | 150–1700 UIT | 3 years (≈ 30-Nov-2027) |
  | ≤ 150 UIT | 4 years (≈ 30-Nov-2028) |

- Failing to designate is leve (Art. 132.9).
- Requirement for xhandler-service: a multi-tenant platform holding invoice data of many end customers plausibly counts as "grandes volúmenes", but no numeric threshold appears in the articles read. Plan to designate a DPO (internal or external), publish its contact, and notify the ANPD within 15 working days. The deadline depends on the operating company's sales bracket.

### 1.9 Data-subject rights (ARCO+) handling
- Reglamento Art. 64: requests go to the responsable, or to the encargado if the contract obliges it to handle them.
- **Art. 66.4**: a request filed with the encargado must be forwarded to the responsable **within 3 days at most**. Art. 66.5: the encargado must also tell the data subject.
- Art. 66.2: the responsable may raise observations within 5 days. The applicant has 5 days to correct.
- **Art. 69** maximum response times, in working days:

  | Right | Deadline |
  |---|---|
  | Información | 8 days |
  | Acceso | 20 days |
  | Rectificación, cancelación, oposición | 10 days |

- Art. 70: one request for more information is allowed within 7 days. The deadline is suspended meanwhile.
- Art. 71: one extension of equal length is allowed, except for the right to information, and must be justified.
- Art. 67.3: rights are free of charge for private banks. Art. 67.1: the procedure must be simple.
- Art. 76: data portability, effective 6 months after the Reglamento entered into force (1st DCF).
- Art. 83: after deletion, tell the data subject which transferees received the data.
- Art. 84: deletion is refused where contractual or legal reasons justify keeping the data.
- Late handling is leve (Art. 132.7). Not handling or obstructing is grave (Art. 133.1).
- Requirement for xhandler-service:
  - (a) A DSAR intake path for end customers. It must route the request to the right tenant within 3 working days and send an acknowledgement to the requester.
  - (b) Tenant tooling to search, export (portable, machine-readable), rectify and suppress an individual's data across documents.
  - (c) A rule that suppression is blocked for issued CPE still inside the tax retention period (Art. 84 plus the Código Tributario, see §6). Use *bloqueo* (Art. III.2) instead.
  - (d) SLA timers that follow the Art. 69 working-day deadlines.

### 1.10 Retention and deletion
- Ley Art. 30, Reglamento Art. 31.2: covered in §1.2 (encargado ≤ 2 years after the last encargo; return the data if a legal retention duty exists).
- Reglamento Art. 132.3: not deleting data that is no longer needed is leve, unless it was anonymised.
- Reglamento Art. 46.3: logs are kept ≥ 2 years.
- Requirement for xhandler-service: a retention policy per data class. Examples: CPE XML/CDR at least 5 years (tax, on behalf of the tenant); audit logs ≥ 2 years; tenant data deleted or returned after termination, with a maximum of 2 years unless retention is expressly authorised.

### 1.11 Sanctions
- Ley 29733 Art. 39:

  | Infraction class | Fine |
  |---|---|
  | Leve | 0.5–5 UIT |
  | Grave | >5–50 UIT |
  | Muy grave | >50–100 UIT |

  A fine may never exceed 10% of the previous year's gross revenue.
- Ley Art. 40: coercive fines up to 10 UIT.
- Ley Art. 38: liability is **objective** (strict).

---

## 2. Digital signatures and certificates (Ley 27269, DS 052-2008-PCM and amendments)

Note: the Ley 27269 PDF from leyes.congreso.gob.pe has broken font encoding, so its text was unreadable. The points below come from the Reglamento, which restates the Ley's key rules.

### 2.1 Validity and the IOFE
- DS 052-2008-PCM Art. 3: a digital signature generated inside the Infraestructura Oficial de Firma Electrónica (IOFE) has the same validity as a handwritten signature.
- Art. 2: signatures from **outside** the IOFE are still valid "en consideración a los pactos o convenios que acuerden las partes".
- Art. 4: documents are admitted as evidence if signed with a certificate from an accredited Entidad de Certificación (EC) and Entidad de Registro (ER), using accredited signature software.
- DS 029-2021-PCM, 1st DCM, adds Art. 1A to DS 052-2008. There are three modalities: simple, advanced, and qualified/digital.
  - Advanced signature requirement: "creada utilizando datos de creación de firmas que el firmante puede utilizar bajo su control".
  - Art. 2A: the burden of proof is reversed only for qualified (digital) signatures.
- Art. 57 (as cited in Res. 118-2021/CFE and Res. 175-2024/DGI): **INDECOPI** is the IOFE's Autoridad Administrativa Competente. Today that role sits with its Dirección de la Gestión de la IOFE (DGI).

### 2.2 Certificates used for SUNAT CPE
- RS 117-2017/SUNAT, numeral 1.17 (definitions; RS 182-2016 uses the same wording): "Firma digital" is the signature in Art. 6 of the DS 052-2008-PCM Reglamento, "que sea válida y esté vigente". The certificate must contain the holder's name or razón social **and RUC**. For an individual, it must also contain the ID document type and number.
- DS 052-2008 Art. 6, as amended by DS 029-2021-PCM: a firma digital has full legal effect when generated by an accredited Prestador de Servicios de Certificación Digital within the IOFE.
- DS 052-2008 Art. 9 / Art. 14: a legal person (persona jurídica) is the *titular*. The *suscriptor* is a natural person, **except certificates for use "a través de agentes automatizados"**, where the legal person is both titular and suscriptor and bears responsibility.
- RS 117-2017/SUNAT (amending the SEE rules): when a PSE acts for an issuer, the PSE performs emission, sending and so on "**utilizando su propio certificado digital** y/o código de usuario y Clave SOL". The issuer "mantiene la responsabilidad". The issuer must register its certificate (or the PSE and the certificate it will use) in SUNAT Operaciones en Línea.
- Requirement for xhandler-service:
  - Accept only certificates whose subject carries the tenant RUC. Validate validity and revocation status.
  - When operating as PSE, sign with the **PSE's own** certificate, which must also be registered with SUNAT.
  - Prefer "agente automatizado" certificates for server-side signing, because they make the legal person both titular and suscriptor (DS 052-2008 Arts. 9 and 14).
  - Whether SUNAT requires the certificate to be IOFE-accredited, or accepts any X.509 certificate, cannot be confirmed from what was read. The RS 117-2017 definition points to Art. 6, which ties full legal effect to the IOFE. However, DS 105-2012-PCM and RS 251-2012/SUNAT (which adapts RS 097-2012 to DS 105-2012-PCM "que… establece disposiciones para facilitar la puesta en marcha de la firma digital") are image-only PDFs and could not be read. **UNVERIFIED.**
  - Whether SUNAT issues a free certificate of its own for CPE: **UNVERIFIED**, because no readable SUNAT source was found and cpe.sunat.gob.pe returned 403. DS 029-2021-PCM (Disposición on SUNAT) does let SUNAT use SOL and the Clave SOL "para generar la firma electrónica" in its own systems.

### 2.3 Can a SaaS hold a client's private key and sign for it? (custody / cloud signing)
- Original DS 052-2008:
  - Art. 7.d: signature generation is "bajo el control exclusivo del suscriptor".
  - Art. 8.a: it is presumed the suscriptor has "control exclusivo de la clave privada".
  - Art. 10.b-c: the suscriptor generates the private key and keeps "control y la reserva" of it.
- DS 029-2021-PCM (2nd DCM) **amended** these rules to allow remote signing:
  - Art. 6: signature created by means, "**incluso a distancia**", that the signer keeps under control "con un elevado grado de confianza".
  - Art. 8.a: the control presumption holds "incluso cuando la misma es gestionada por un **Prestador de Servicios de Valor Añadido acreditado en la modalidad de sistema de creación de firma remota**".
  - Art. 10.b: the suscriptor generates the key or authorises remote generation by such an accredited PSVA.
  - Art. 10.c: the PSVA bears responsibility for the key.
  - Art. 15.c: the titular does not have to cancel the certificate on key compromise when an accredited PSVA manages the key.
  - Art. 33.d: PSVA functions include "gestión de claves privadas y creación de firmas digitales remotas".
  - Art. 35.c: the new modality "Sistema de creación de firma remota… utilizando claves privadas que se encuentran localizadas remotamente y son gestionadas por un tercero".
- DS 029-2021-PCM, 4th DCT: providers holding a national or international certification could be "recognised" for **18 months** after publication. Res. 175-2024/DGI says that window has closed.
- **Res. 175-2024/DGI-INDECOPI** (24-Jun-2024) sets the requirements to be accredited as PSVA in the Firma Remota modality:
  - legal existence and powers of the representative;
  - a descriptive report and organisation chart;
  - **domicile in Peru**;
  - an **insurance policy** expressly covering remote signing;
  - **certifications** to CEN EN 419 241-1:2018 and 419 241-2:2019, or alternatively CEN EN 419 221-5 (cryptographic module, i.e. HSM), or ETSI TS 119 431-1/-2 plus TS 119 432;
  - payment of the procedure fee.
- Res. 118-2021/CFE is the earlier version of the same standards. Res. 175-2025/DGI covers audits for firma-remota accreditation; only its title was read.
- Requirement for xhandler-service:
  - Storing a tenant's `.pfx` and signing on its behalf is **not** the accredited "firma remota" service. A server-side signature with a customer-uploaded key held by a non-accredited provider probably does not get the Art. 8 presumptions as an IOFE digital signature. It still has effect between the parties under Art. 2 (contractual agreement) and Art. 1A (advanced or simple signature).
  - What SUNAT itself demands is in §2.2 and is **UNVERIFIED**.
  - Mitigations:
    - (a) A tenant contract clause that expressly authorises xhandler-service to store the key and sign CPE as the tenant's agent, with the tenant keeping responsibility.
    - (b) Advise "agente automatizado" certificates.
    - (c) Keep keys in an HSM or KMS, encrypted per tenant, with no operator access, and log every signing operation.
    - (d) Offer PSE mode, where signing uses the PSE's own certificate, as SUNAT allows.
    - (e) If a "firma remota" product is ever offered as a legal digital signature, it requires INDECOPI PSVA accreditation, a Peruvian domicile, insurance, and CEN EN 419 241 / 419 221-5 or ETSI certification.
  - Nothing read requires an HSM for ordinary CPE signing. The HSM and QSCD standards apply only to accredited remote signing.

---

## 3. Digital trust and cybersecurity (DU 007-2020 and its Reglamento DS 126-2025-PCM; DL 1412)

- DU 007-2020 Art. 3.i defines a "Servicio digital": a service provided totally or partly through the Internet, automated and data-intensive, for acquiring services or information, accessing data, paying, and so on.
- DU 007-2020 Art. 9.1: the obligations (notify the CNSD of **every** digital security incident, implement security measures, manage risk) apply to:
  - public entities;
  - digital service providers in the **financial sector, basic utilities, health and passenger transport**;
  - ISPs;
  - providers of **critical activities**;
  - educational services.
- Reglamento DS 126-2025-PCM:
  - Art. 5.1: a PSD (Proveedor de Servicios Digitales) is any organisation "independientemente de su localización geográfica" providing digital services through the Internet in Peru.
  - Art. 5.4: cloud, hosting and similar intermediaries are not PSDs.
  - Art. 12.1: the security rules bind private organisations providing the Art. 9.1 services. **Art. 12.2**: other private organisations "consideran como **referenciales**" these rules.
  - Art. 13.1: private-sector critical activities also include "aquellas relacionadas con la **IOFE** en las que tengan participación", the CNSD, the SNTD and "el **ecosistema de pagos digitales**".
  - Art. 15: a private Oficial de Seguridad y Confianza Digital is optional.
  - Art. 17: authentication assurance levels (NCA). Level 2 means 2 factors. Level 3 means 2 factors, one based on a tamper-resistant crypto module. PSDs pick the level by risk (Art. 17.2).
  - Art. 18.1 obligations for the Art. 9.1 PSDs: notify the CNSD; implement controls; manage risk; authenticate people per NCA; report to the ANPD; secure and scalable infrastructure; coordinate incidents with third parties; proactive vulnerability testing.
  - Art. 29.2: private organisations **outside** Art. 9 "**pueden** notificar" incidents to the Registro Nacional.
  - Art. 31: channels are the CNSD platform (requires an OSCD account), encrypted email, phone, and others. Minimum content: description, classification, impact.
  - **Art. 32.1**: incidents classified **critical** must be notified to the CNSD within **48 hours** of knowledge. Others follow the SGTD classification.
  - Art. 33.3: private PSDs may use the SGTD classification as a reference.
  - Art. 34.4: a PSD must make its own third-party providers report incidents to it.
  - 6th and 7th DCF: SGTD will approve the national incident classification within 120 working days, and the protocols, channels and deadlines within 90 working days. Whether these were issued: **UNVERIFIED**.
- Link to data protection: DS 016-2024-JUS Art. 34.5 requires personal-data incidents "en y/o mediante el entorno digital" to be notified to the **CNSD** as well as the ANPD. **This applies to any responsable, not only Art. 9.1 PSDs.**
- DL 1412 Art. 2: the Ley de Gobierno Digital applies to public administration and to private parties exercising administrative powers. It does **not** generally bind a private SaaS.
- Requirement for xhandler-service:
  - (a) An invoicing SaaS is probably not an Art. 9.1 sector PSD, so the DU 007-2020 Art. 9 obligations are only a reference for it (Reglamento Art. 12.2).
    - Exception to watch: as a SUNAT-authorised PSE, check with counsel whether it falls within "actividades críticas" or the "ecosistema de pagos digitales". It is not an IOFE participant unless accredited.
  - (b) Regardless of (a), personal-data incidents in the digital environment must be notified to both the CNSD and the ANPD (DS 016-2024-JUS Art. 34.5). Register an account on the CNSD platform ahead of time.
  - (c) Use 2FA (NCA level 2) for tenant users, and NCA level 3 (hardware key) for operators and anyone who can trigger signing.
  - (d) Contractually require sub-processors to report incidents to xhandler-service (Art. 34.4 by analogy).
  - (e) Run periodic vulnerability testing.
- Ley 30999 (Ciberdefensa) and possible sector rules not read: **UNVERIFIED**.

---

## 4. Consumer protection, contracts, Libro de Reclamaciones

- CPDC (Ley 29571) Art. IV.1.1: a consumer acts "en un ámbito ajeno a una actividad empresarial o profesional". Buying a product meant for one's business activity is excluded.
- **CPDC Art. IV.1.2**: also protected are "**microempresarios** que evidencien una situación de asimetría informativa con el proveedor respecto de aquellos productos o servicios que **no formen parte del giro propio del negocio**".
- Art. III.2: the CPDC applies when consumer relations are concluded in Peru or have effects there.
- **Art. 150** (as amended by **Ley 32495**, 11-Nov-2025): commercial establishments "así como **toda plataforma digital de comercio electrónico**, deben contar con un libro de reclamaciones, en forma física o virtual".
- Art. 151 (Ley 32495): digital platforms must "exhiben permanentemente en un lugar visible… el **enlace** al libro de reclamaciones".
- Art. 152: complaints must be answered within the Art. 24.1 deadline, and the book sent to INDECOPI on request.
- **Art. 24.1**: complaints must be answered within **15 working days, non-extendable**.
- Art. 24.4 (DL 1729, 12-Feb-2026): e-commerce providers must have accessible, effective, always-on complaint and dispute mechanisms.
- Art. 24.5: **non-domiciled** providers targeting Peru without a complaint mechanism must at least keep a working email address for complaints, returns and so on.
- New Art. 56 item h (DL 1729, métodos comerciales coercitivos): dark patterns in digital interfaces are banned.
- Reglamento del Libro de Reclamaciones, DS 011-2011-PCM (original text read):
  - Art. 4: providers that also sell through virtual means must have a **virtual** Libro accessible in the same medium. The consumer must be able to print a free copy and receive it by email.
  - Art. 12: complaint sheets are kept **2 years**.
  - Art. 11: complaints are sent to INDECOPI on request.
  - Later amendments are not consolidated in this text. DS 101-2022-PCM amended Art. 6 num. 3.4 and Art. 6-A.2 b (title read, PDF image-only). Whether the reglamento was adapted to Ley 32495: **UNVERIFIED**.
- Requirement for xhandler-service:
  - (a) Even though the product is B2B, micro-enterprise customers (many SUNAT-obliged SMEs) can count as consumers for software outside their core business (Art. IV.1.2).
  - (b) The sign-up and checkout site is very likely a "plataforma digital de comercio electrónico" under Ley 32495. Put a **virtual Libro de Reclamaciones** on the site with a permanently visible link. It must produce a printable or emailed copy of each sheet. Answer within 15 working days. Keep sheets 2 years. Be able to send them to INDECOPI.
  - (c) If operated from abroad without a complaint system, at least keep a complaint email (Art. 24.5). A Libro is still advisable.
  - (d) Clear pricing and terms. No dark patterns in subscription or cancellation flows.
  - (e) Resellers selling under their own brand have their own provider obligations. Contracts should allocate them.

---

## 5. Taxes on the SaaS itself

- IGV (TUO Ley del IGV):
  - Art. 1.b: IGV covers "utilización en el país de servicios prestados por no domiciliados". Art. 3.c: a service is used in Peru when consumed or employed there.
  - Art. 3.c, as added by **DL 1623** (published 04-Aug-2024): for **individuals without business activity**, digital services are used in Peru if the user habitually resides there. Residence is shown by any of: Peruvian IP or geolocation; Peruvian SIM; payment with a Peruvian-issued card or financial product; or a Peruvian address registered with the provider.
  - Art. 3.h defines "servicios digitales" (automatic, Internet-delivered, not viable without IT). Examples include storage of information, among others.
  - DS 157-2024-EF, the regulation, was published 24-Aug-2024 and is in force from the next day. Per the SUNAT note, DL 1623 took effect then, except Art. 49-B, which takes effect when regulated.
- Art. 9.1.c: whoever "utilicen en el país servicios prestados por no domiciliados" is the IGV taxpayer. For B2B, the Peruvian customer self-assesses IGV on a foreign provider's service.
- Art. 9.2.i (DL 1623): individuals without business activity are taxpayers when they use digital services from non-domiciled providers.
- **Art. 49-A** (DL 1623): when the user is an individual without business activity, the **non-domiciled provider becomes the IGV collection agent** (agente de percepción or retención) from the first day of the month after it starts operating in Peru.
  - It must **register in the RUC**.
  - It must file and pay monthly within the first 10 working days, in PEN or USD.
  - Art. 49-A.3.b: it identifies B2C users through registration data. If the platform cannot distinguish, the user is treated as an individual without business activity.
  - Art. 49-B: if the provider does not register, file or pay, payment facilitators (banks, e-money issuers) collect the tax instead.
- Income tax, TUO LIR Art. 9.i: income from digital services is Peruvian-source "cuando el servicio se utilice económicamente, use o consuma en el país". The withholding rate on payments to non-domiciled providers was not read: **UNVERIFIED**.
- Requirement for xhandler-service:
  - (a) **Peruvian operating company:** a domiciled provider charges 18% IGV (rate not verified here) on subscriptions to all customers. It must be a SUNAT electronic issuer and issue a factura (RUC customers) or boleta (individuals) for every subscription charge.
    - Dogfooding: the Lago billing flow should call xhandler-service itself to issue the CPE. Lago invoices are not valid comprobantes.
    - Reseller commissions need their own CPE flows (the reseller invoices xhandler-service, or vice versa, depending on the model).
  - (b) **Foreign operating company:** B2B customers (RUC holders with business activity) self-assess IGV as users of non-domiciled services (Art. 9.1.c), and must withhold income tax at the applicable rate.
    - If any customer is an individual without business activity (possible for small issuers), xhandler-service must register in the RUC and act as IGV collection agent (Art. 49-A).
    - The sign-up flow must capture user type, name, DNI and RUC so B2B can be told apart from B2C.
    - A foreign company cannot issue Peruvian CPE unless registered. Customers would need the foreign invoice to support cost and withholding. Check the tax-deduction implications with tax counsel.
  - (c) Either way, record the customer's RUC and DNI and their type to determine IGV treatment.

---

## 6. Data residency / localisation of tax documents

- Código Tributario Art. 87.7 (DL 1372): taxpayers must store, archive and conserve books, records, documents and supporting records "cinco (5) años o durante el plazo de prescripción del tributo, el que fuera mayor", counted from 1 January of the year after the filing due date.
  - Loss or destruction must be reported to SUNAT within 15 working days.
  - When documents are electronic, "la SUNAT podrá sustituirlo en el almacenamiento, archivo y conservación".
- Código Tributario Art. 87.8: the electronic systems and storage media holding taxable data must be kept operational for the same period. Any impediment must be reported.
- No provision read requires the XML or CDR, or the processor's servers, to be **physically located in Peru**. That includes Código Tributario Art. 87, the IGV/LIR provisions read, Ley 29733, DS 016-2024-JUS, DU 007-2020 and its Reglamento, and DS 029-2021-PCM (searched for localización, territorio nacional and nube). Personal-data export is allowed subject to the flujo transfronterizo rules (§1.5).
- SUNAT CPE-specific conservation rules (RS 097-2012, 300-2014 and others) were not read here. Whether they impose location, format or delivery-on-request conditions: **UNVERIFIED**. These are covered by the separate SUNAT research.
- Requirement for xhandler-service:
  - (a) Offshore hosting is legally possible, but it triggers the ANPD cross-border notification and safeguards (§1.5).
  - (b) Retain signed XML and CDR for each tenant for at least 5 years or the limitation period. Allow export at any time, so the tenant, who carries the Art. 87.7 duty, can produce them to SUNAT.
  - (c) Plan for tenant churn: hand over the full archive, or retain it under an express retention agreement (§1.2).
  - (d) Keep the archive readable and the system operational (Art. 87.8): format stability and key and certificate history for verification.
  - (e) Disaster recovery that meets weekly backups at minimum (DS 016-2024-JUS Art. 51) and the 15-working-day loss-reporting duty.

---

## Unverified / could not access

- **Ley 27269 text**: the leyes.congreso.gob.pe PDF has garbled font encoding. Its articles are not cited directly; only the Reglamento was used. `www.indecopi.gob.pe` did not resolve (DNS), so its IOFE pages were not read.
- **Ley 29733 PDF from leyes.congreso.gob.pe** is image-only. The gob.pe compiled version (May 2023) was used instead. Amendments after 2023 were not checked.
- **DS 105-2012-PCM** and **RS 251-2012/SUNAT** are image-only PDFs. Their content is unknown: possible transitional acceptance of non-IOFE certificates for SUNAT CPE. **RS 097-2012/SUNAT** is also image-only.
- Whether SUNAT requires IOFE-accredited certificates for CPE today, and whether SUNAT provides a free certificate. cpe.sunat.gob.pe returned HTTP 403. **UNVERIFIED.**
- Whether the ANPD has published the **model contractual clauses** (DS 016-2024-JUS Art. 20.2), any **adequacy resolutions** (Art. 19), the **flujo transfronterizo form**, and incident-notification **lineamientos** (Art. 34.7).
- Any numeric threshold for "grandes volúmenes" (DS 016-2024-JUS Arts. 34.1 and 37.1). None was found in the text.
- Whether an encargado must register a bank in the Registro Nacional for tenant data. The articles read put the duty on the titular.
- DU 007-2020 Reglamento follow-up norms: incident classification, protocols, channels and deadlines (6th and 7th DCF). Not checked. The exact entry-into-force date of DS 126-2025-PCM was not read; the DS PDF is image-only. The gob.pe page date is 04-Nov-2025.
- The consolidated Libro de Reclamaciones Reglamento with all amendments, including the full text of DS 101-2022-PCM (image-only), and any adaptation to Ley 32495.
- The IGV rate (18%), the income-tax withholding rate on payments for digital services to non-domiciled providers (TUO LIR Art. 56), and DS 157-2024-EF details. Not read.
- The ANPD DPO notification procedure and form, and UIT values for fines. Not read.
- Ley 30999 (Ciberdefensa) and any sector-specific rules. Not read.
- gob.pe norm pages returned HTTP 418 to WebFetch. curl worked for all gob.pe pages used. No bot checks were bypassed.
