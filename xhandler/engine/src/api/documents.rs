//! Operations on documents that don't contact SUNAT: validate (XSD + rules),
//! inspect, and the OSE CDR builder.

use axum::extract::Json;
use chrono::{DateTime, FixedOffset};
use serde::{Deserialize, Serialize};
use xhandler::prelude::UblFile;

use super::{blocking, lima_today, ApiError, ApiJson, ErrorKind};
use crate::cdr;
use crate::inspect::{self, InspectResponse};
use crate::rules::{self, Finding};
use crate::xmldsig;
use crate::xsd::{self, XsdOutcome};

/// SUNAT's code for an XML it can't parse, "incluye validación de XSD"
/// (AjustesValidacionesCPE, General sheet)
const UNPARSEABLE: &str = "0306";
/// SUNAT's code for an altered document (a signature that doesn't verify)
const ALTERED: &str = "2335";
/// Code of the XSD violations (SUNAT reports them all as 0306)
const XSD: &str = "XSD";
/// libxml2's diagnostic for an `ext:ExtensionContent` without children
const EMPTY_SIGNATURE_SLOT: &str = "}ExtensionContent': Missing child element(s)";

// --- validate ---------------------------------------------------------------

#[derive(Deserialize)]
pub struct ValidateRequest {
    pub xml: String,
}

#[derive(Serialize, Debug)]
pub struct ValidateResponse {
    pub valid: bool,
    pub errors: Vec<Finding>,
    pub observations: Vec<Finding>,
}

pub(super) async fn validate(
    ApiJson(request): ApiJson<ValidateRequest>,
) -> Result<Json<ValidateResponse>, ApiError> {
    let unparseable = |detail: &str| {
        Json(ValidateResponse {
            valid: false,
            errors: vec![Finding {
                code: UNPARSEABLE.into(),
                message: format!("No se puede leer (parsear) el archivo XML: {detail}"),
            }],
            observations: Vec::new(),
        })
    };
    if request.xml.len() > xsd::MAX_XML_BYTES {
        return Ok(unparseable("el archivo es demasiado grande"));
    }
    // One quick pass refuses DOCTYPEs and reads what the rules need
    let facts = match inspect::read_facts(&request.xml) {
        Ok(facts) => facts,
        Err(detail) => return Ok(unparseable(&detail)),
    };
    // The bytes as sent to SUNAT (in the encoding the XML declares)
    let bytes = UblFile::from(request.xml).bytes().to_vec();
    // A signed document must verify: an OSE never answers for an altered one (§11.4)
    let signature = if facts.digest_value.is_some() {
        let signed = bytes.clone();
        Some(
            blocking(move || Ok::<_, ApiError>(xmldsig::verify(&signed)))
                .await?
                .0,
        )
    } else {
        None
    };
    let mut errors = match xsd::validate(&facts.document_type, bytes)
        .await
        .map_err(|e| ApiError::new(ErrorKind::Other, e))?
    {
        XsdOutcome::Valid | XsdOutcome::NoSchema => Vec::new(),
        XsdOutcome::Unparseable => return Ok(unparseable("XML mal formado")),
        XsdOutcome::Invalid(messages) => messages
            .into_iter()
            // Documents are validated before signing (DESIGN.md §4): the
            // empty signature slot of an unsigned document is fine
            .filter(|message| {
                facts.digest_value.is_some() || !message.contains(EMPTY_SIGNATURE_SLOT)
            })
            .map(|message| Finding {
                code: XSD.into(),
                message,
            })
            .collect(),
    };
    let (rule_errors, observations) = rules::check(&facts, lima_today());
    errors.extend(rule_errors);
    match signature {
        None | Some(xmldsig::Verdict::Valid) => {}
        Some(xmldsig::Verdict::DigestMismatch) => errors.push(Finding {
            code: ALTERED.into(),
            message: "El documento electrónico ingresado ha sido alterado (el resumen no coincide con la firma)".into(),
        }),
        Some(xmldsig::Verdict::BadSignature) => errors.push(Finding {
            code: ALTERED.into(),
            message: "La firma digital no corresponde al certificado del documento".into(),
        }),
        Some(xmldsig::Verdict::Unsupported(reason)) => errors.push(Finding {
            code: ALTERED.into(),
            message: format!("No se puede verificar la firma digital: {reason}"),
        }),
    }
    Ok(Json(ValidateResponse {
        valid: errors.is_empty(),
        errors,
        observations,
    }))
}

// --- inspect ----------------------------------------------------------------

#[derive(Deserialize)]
pub struct InspectRequest {
    pub xml: String,
}

pub(super) async fn inspect(
    ApiJson(request): ApiJson<InspectRequest>,
) -> Result<Json<InspectResponse>, ApiError> {
    blocking(move || {
        inspect::inspect(&request.xml).map_err(|e| ApiError::new(ErrorKind::InvalidDocument, e))
    })
    .await
}

// --- cdr --------------------------------------------------------------------

#[derive(Deserialize)]
pub struct CdrNote {
    pub code: String,
    pub message: String,
}

#[derive(Deserialize)]
pub struct CdrRequest {
    /// The issuer's signed document
    pub xml: String,
    pub ose_ruc: String,
    /// "0" (accepted) by default
    #[serde(default = "accepted")]
    pub response_code: String,
    pub description: String,
    #[serde(default)]
    pub notes: Vec<CdrNote>,
    /// When the OSE checked the document (RFC 3339)
    pub issued_at: DateTime<FixedOffset>,
    /// When the OSE received it; `issued_at` by default
    pub received_at: Option<DateTime<FixedOffset>>,
    /// Who sent the document (issuer or PSE); the issuer by default
    pub sender_ruc: Option<String>,
    /// CDR ID (UUID); derived from the inputs by default
    pub id: Option<String>,
}

fn accepted() -> String {
    "0".into()
}

#[derive(Serialize)]
pub struct CdrResponse {
    /// Unsigned CDR, with an empty ext:ExtensionContent for the signature
    pub xml: String,
}

pub(super) async fn cdr(
    ApiJson(request): ApiJson<CdrRequest>,
) -> Result<Json<CdrResponse>, ApiError> {
    let is_ruc = |value: &str| value.len() == 11 && value.bytes().all(|b| b.is_ascii_digit());
    if !is_ruc(&request.ose_ruc) || request.sender_ruc.as_deref().is_some_and(|r| !is_ruc(r)) {
        return Err(ApiError::new(
            ErrorKind::InvalidRequest,
            "ose_ruc and sender_ruc must be RUCs (11 digits)",
        ));
    }
    blocking(move || {
        let facts = inspect::read_facts(&request.xml)
            .map_err(|e| ApiError::new(ErrorKind::InvalidDocument, e))?;
        let notes: Vec<cdr::Note> = request
            .notes
            .into_iter()
            .map(|note| cdr::Note {
                code: note.code,
                message: note.message,
            })
            .collect();
        let xml = cdr::build(&cdr::CdrInput {
            facts: &facts,
            ose_ruc: &request.ose_ruc,
            sender_ruc: request.sender_ruc.as_deref(),
            response_code: &request.response_code,
            description: &request.description,
            notes: &notes,
            issued_at: request.issued_at,
            received_at: request.received_at,
            id: request.id.as_deref(),
        })
        .map_err(|e| ApiError::new(ErrorKind::InvalidDocument, e))?;
        Ok(CdrResponse { xml })
    })
    .await
}

#[cfg(test)]
mod tests {
    use super::super::tests::{build_invoice, invoice, post, sign_with_demo_key};
    use super::super::{BETA_CERTIFICATE, BETA_PRIVATE_KEY};
    use axum::http::StatusCode;
    use base64::Engine as _;
    use xhandler::prelude::RsaKeyPair;

    async fn validate(xml: &serde_json::Value) -> serde_json::Value {
        let (status, body) = post("/v1/validate", serde_json::json!({ "xml": xml })).await;
        assert_eq!(status, StatusCode::OK, "{body}");
        body
    }

    async fn build(document: serde_json::Value) -> serde_json::Value {
        let (status, body) = post(
            "/v1/build",
            serde_json::json!({ "document": document, "fecha_emision": super::lima_today() }),
        )
        .await;
        assert_eq!(status, StatusCode::OK, "{body}");
        body["xml"].clone()
    }

    #[tokio::test]
    async fn built_and_signed_invoice_is_valid() {
        let xml = build(invoice()).await;
        let body = validate(&xml).await;
        assert_eq!(body["valid"], true, "{body}");
        let signed = sign_with_demo_key(xml).await;
        let body = validate(&signed["xml"]).await;
        assert_eq!(body["valid"], true, "{body}");
    }

    #[tokio::test]
    async fn altered_documents_and_signatures_are_2335() {
        let signed = sign_with_demo_key(build(invoice()).await).await;
        let xml = signed["xml"].as_str().unwrap().to_string();
        // A changed line after signing
        let altered = xml.replacen("Item1", "Item9", 1);
        assert_ne!(altered, xml);
        let body = validate(&altered.into()).await;
        assert_eq!(body["valid"], false);
        assert_eq!(body["errors"][0]["code"], "2335", "{body}");
        assert!(body["errors"][0]["message"]
            .as_str()
            .unwrap()
            .contains("alterado"));
        // A forged signature value
        let start = xml.find("<ds:SignatureValue>").unwrap() + "<ds:SignatureValue>".len();
        let mut forged = xml.clone();
        forged.replace_range(
            start..start + 4,
            if &xml[start..start + 4] == "AAAA" {
                "BBBB"
            } else {
                "AAAA"
            },
        );
        let body = validate(&forged.into()).await;
        assert_eq!(body["errors"][0]["code"], "2335", "{body}");
        assert!(
            body["errors"][0]["message"]
                .as_str()
                .unwrap()
                .contains("certificado"),
            "{body}"
        );
    }

    #[tokio::test]
    async fn xsd_violations_and_rules_are_reported() {
        let xml = build(invoice()).await;
        let xml = xml.as_str().unwrap().replacen(
            "<cbc:IssueDate>",
            "<cbc:Unexpected>x</cbc:Unexpected><cbc:IssueDate>",
            1,
        );
        let body = validate(&xml.into()).await;
        assert_eq!(body["valid"], false);
        assert_eq!(body["errors"][0]["code"], "XSD", "{body}");

        let mut document = invoice();
        document["spec"]["serie_numero"] = "X001-1".into();
        document["spec"]["fecha_emision"] = "2099-01-01".into();
        let (_, built) = post("/v1/build", serde_json::json!({ "document": document })).await;
        let body = validate(&built["xml"]).await;
        let codes: Vec<&str> = body["errors"]
            .as_array()
            .unwrap()
            .iter()
            .map(|e| e["code"].as_str().unwrap())
            .collect();
        assert!(codes.contains(&"2329"), "{body}");
    }

    #[tokio::test]
    async fn unparseable_and_doctype_are_0306() {
        for xml in [
            "<Invoice>",
            r#"<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><Invoice>&e;</Invoice>"#,
        ] {
            let body = validate(&xml.into()).await;
            assert_eq!(body["valid"], false);
            assert_eq!(body["errors"][0]["code"], "0306", "{body}");
        }
    }

    #[tokio::test]
    async fn other_document_types_validate() {
        let summary = serde_json::json!({
            "kind": "SummaryDocuments",
            "spec": {
                "numero": 1,
                "fecha_emision_comprobantes": "2026-11-14",
                "proveedor": { "ruc": "12345678912", "razon_social": "Softgreen S.A.C." },
                "comprobantes": [{
                    "tipo_operacion": "1",
                    "comprobante": {
                        "tipo_comprobante": "03",
                        "serie_numero": "B001-1",
                        "cliente": { "tipo_documento_identidad": "1", "numero_documento_identidad": "12345678" },
                        "valor_venta": { "importe_total": 118, "gravado": 100 },
                        "impuestos": { "igv": 18, "igv_tasa": 18 }
                    }
                }]
            }
        });
        let body = validate(&build(summary).await).await;
        assert_eq!(body["valid"], true, "{body}");

        let mut credit_note = serde_json::json!({
            "kind": "CreditNote",
            "spec": {
                "serie_numero": "FC01-1",
                "comprobante_afectado_serie_numero": "F001-1",
                "descripcion_sustento": "Anulación de operación",
                "proveedor": { "ruc": "12345678912", "razon_social": "Softgreen S.A.C." },
                "cliente": { "tipo_documento_identidad": "6", "numero_documento_identidad": "12121212121", "nombre": "Carlos Feria" },
                "detalles": [{ "descripcion": "Item1", "cantidad": 10, "precio": 100 }]
            }
        });
        let body = validate(&build(credit_note.clone()).await).await;
        assert_eq!(body["valid"], true, "{body}");
        credit_note["spec"]["serie_numero"] = "ZC01-1".into();
        let body = validate(&build(credit_note).await).await;
        assert_eq!(body["errors"][0]["code"], "1001", "{body}");
    }

    #[tokio::test]
    async fn inspect_reads_a_signed_invoice() {
        let signed = sign_with_demo_key(build_invoice().await["xml"].clone()).await;
        let (status, body) = post("/v1/inspect", serde_json::json!({ "xml": signed["xml"] })).await;
        assert_eq!(status, StatusCode::OK, "{body}");
        assert_eq!(body["document_type"], "Invoice");
        assert_eq!(body["tipo_comprobante"], "01");
        assert_eq!(body["ruc"], "12345678912");
        assert_eq!(body["serie"], "F001");
        assert_eq!(body["numero"], 1);
        assert_eq!(body["serie_numero"], "F001-1");
        assert_eq!(body["fecha_emision"], "2026-11-15");
        assert_eq!(body["moneda"], "PEN");
        assert_eq!(body["total"], "1180.00");
        assert_eq!(body["customer_tipo"], "6");
        assert_eq!(body["customer_numero"], "12121212121");
        assert_eq!(body["signed"], true);
        assert_eq!(body["digest_value"], signed["digest_value"]);
        assert_eq!(body["sha256"].as_str().unwrap().len(), 64);
    }

    #[tokio::test]
    async fn inspect_refuses_doctype() {
        let (status, body) = post(
            "/v1/inspect",
            serde_json::json!({ "xml": "<!DOCTYPE x><Invoice/>" }),
        )
        .await;
        assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY);
        assert_eq!(body["kind"], "invalid_document");
    }

    /// The CDR is valid UBL 2.1, and the app can sign it in two steps with
    /// the OSE key: xsigner finds the empty extension slot.
    #[tokio::test]
    async fn cdr_validates_and_signs_in_two_steps() {
        let signed = sign_with_demo_key(build_invoice().await["xml"].clone()).await;
        let (status, cdr) = post(
            "/v1/cdr",
            serde_json::json!({
                "xml": signed["xml"],
                "ose_ruc": "20100000001",
                "response_code": "0",
                "description": "La Factura numero F001-1, ha sido aceptada",
                "notes": [{ "code": "4252", "message": "El dato ingresado como atributo @listName es incorrecto." }],
                "issued_at": "2026-11-16T10:00:00-05:00",
            }),
        )
        .await;
        assert_eq!(status, StatusCode::OK, "{cdr}");
        let xml = cdr["xml"].as_str().unwrap();
        assert!(xml.contains(&format!(
            "<cbc:DocumentHash>{}</cbc:DocumentHash>",
            signed["digest_value"].as_str().unwrap()
        )));
        let body = validate(&cdr["xml"]).await;
        assert_eq!(body["valid"], true, "{body}");

        let (status, prepared) = post("/v1/sign/prepare", serde_json::json!({ "xml": xml })).await;
        assert_eq!(status, StatusCode::OK, "{prepared}");
        let key_pair = RsaKeyPair::from_pem(BETA_PRIVATE_KEY, BETA_CERTIFICATE).unwrap();
        let data = base64::engine::general_purpose::STANDARD
            .decode(prepared["data_to_sign"].as_str().unwrap())
            .unwrap();
        let signature = key_pair.sign(&data).unwrap();
        let (status, finalized) = post(
            "/v1/sign/finalize",
            serde_json::json!({
                "xml": xml,
                "signature_value": base64::engine::general_purpose::STANDARD.encode(signature),
                "certificate": base64::engine::general_purpose::STANDARD.encode(key_pair.certificate_der()),
            }),
        )
        .await;
        assert_eq!(status, StatusCode::OK, "{finalized}");
        let signed_cdr = finalized["xml"].as_str().unwrap();
        assert!(signed_cdr.contains("<ext:ExtensionContent><ds:Signature"));
        let body = validate(&finalized["xml"]).await;
        assert_eq!(body["valid"], true, "{body}");
    }

    #[tokio::test]
    async fn cdr_needs_a_ruc_for_the_ose() {
        let (status, body) = post(
            "/v1/cdr",
            serde_json::json!({
                "xml": build_invoice().await["xml"],
                "ose_ruc": "OSE",
                "description": "x",
                "issued_at": "2026-11-16T10:00:00-05:00",
            }),
        )
        .await;
        assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY);
        assert_eq!(body["kind"], "invalid_request");
    }
}
