//! OSE CDR builder (DESIGN.md §19 R4): the `ApplicationResponse` an OSE
//! returns for a document it accepted, following SUNAT's *CDR-OSE-Comprobante*
//! sheet (AjustesValidacionesCPE v2026-02-12): UBL 2.1, CustomizationID 1.0.
//!
//! The CDR is unsigned, with an empty `ext:ExtensionContent` where xsigner
//! puts the signature (`/v1/sign/prepare` + `/v1/sign/finalize`, with the OSE
//! key in the HSM).
//!
//! Parties follow SUNAT's sheet, not SUNAT's own CDR: `SenderParty` is whoever
//! sent the document (the issuer, or its PSE), `ReceiverParty` is the OSE, and
//! the issuer and buyer go in `DocumentResponse` (`IssuerParty`,
//! `RecipientParty`).

use chrono::{DateTime, FixedOffset};
use sha2::{Digest, Sha256};

use crate::inspect::Facts;

/// `Id` of the ds:Signature xsigner adds; cac:Signature points at it
const SIGNATURE_ID: &str = xhandler::xsigner::SIGNATURE_ID;
/// Peru has no daylight saving time: America/Lima is always UTC-5
const LIMA_OFFSET_SECONDS: i32 = 5 * 3600;
const CATALOG_6_URI: &str = "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo6";
const RETURN_CODES_URI: &str = "urn:pe:gob:sunat:cpe:see:gem:codigos:codigoretorno";

pub struct Note {
    pub code: String,
    pub message: String,
}

pub struct CdrInput<'a> {
    /// The issuer's document, as received
    pub facts: &'a Facts,
    pub ose_ruc: &'a str,
    /// RUC of who sent the document (issuer or PSE); the issuer's by default
    pub sender_ruc: Option<&'a str>,
    /// "0" for an accepted document (the only value SUNAT expects in a CDR)
    pub response_code: &'a str,
    pub description: &'a str,
    pub notes: &'a [Note],
    /// When the OSE checked the document (ResponseDate/Time)
    pub issued_at: DateTime<FixedOffset>,
    /// When the OSE received it (IssueDate/Time); `issued_at` by default
    pub received_at: Option<DateTime<FixedOffset>>,
    /// CDR ID (a UUID); derived from the inputs by default
    pub id: Option<&'a str>,
}

pub fn build(input: &CdrInput) -> Result<String, String> {
    let facts = input.facts;
    let issuer = facts
        .supplier_ruc
        .as_deref()
        .ok_or("el documento no tiene el RUC del emisor")?;
    let serie_numero = facts
        .id
        .as_deref()
        .ok_or("el documento no tiene serie y número (cbc:ID)")?;
    let lima = FixedOffset::west_opt(LIMA_OFFSET_SECONDS).expect("valid offset");
    let checked = input.issued_at.with_timezone(&lima);
    let received = input
        .received_at
        .unwrap_or(input.issued_at)
        .with_timezone(&lima);
    let id = match input.id {
        Some(id) => id.to_string(),
        None => derived_uuid(input),
    };
    let sender = input.sender_ruc.unwrap_or(issuer);

    let mut xml = String::with_capacity(4096);
    xml.push_str(r#"<?xml version="1.0" encoding="UTF-8"?>"#);
    xml.push_str(concat!(
        r#"<ApplicationResponse xmlns="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2""#,
        r#" xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2""#,
        r#" xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2""#,
        r#" xmlns:ext="urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2">"#,
        "<ext:UBLExtensions><ext:UBLExtension><ext:ExtensionContent></ext:ExtensionContent></ext:UBLExtension></ext:UBLExtensions>",
        "<cbc:UBLVersionID>2.1</cbc:UBLVersionID>",
        "<cbc:CustomizationID>1.0</cbc:CustomizationID>",
    ));
    element(&mut xml, "cbc:ID", &id);
    element(
        &mut xml,
        "cbc:IssueDate",
        &received.format("%Y-%m-%d").to_string(),
    );
    element(
        &mut xml,
        "cbc:IssueTime",
        &received.format("%H:%M:%S").to_string(),
    );
    element(
        &mut xml,
        "cbc:ResponseDate",
        &checked.format("%Y-%m-%d").to_string(),
    );
    element(
        &mut xml,
        "cbc:ResponseTime",
        &checked.format("%H:%M:%S").to_string(),
    );
    // Also as cbc:Note, like SUNAT's CDRs, so CDR readers (xsender) see them
    for note in input.notes {
        element(
            &mut xml,
            "cbc:Note",
            &format!("{} - {}", note.code, note.message),
        );
    }
    xml.push_str("<cac:Signature>");
    element(&mut xml, "cbc:ID", SIGNATURE_ID);
    xml.push_str("<cac:SignatoryParty><cac:PartyIdentification>");
    element(&mut xml, "cbc:ID", input.ose_ruc);
    xml.push_str("</cac:PartyIdentification></cac:SignatoryParty>");
    xml.push_str("<cac:DigitalSignatureAttachment><cac:ExternalReference>");
    element(&mut xml, "cbc:URI", &format!("#{SIGNATURE_ID}"));
    xml.push_str("</cac:ExternalReference></cac:DigitalSignatureAttachment></cac:Signature>");
    party(&mut xml, "cac:SenderParty", "6", sender);
    party(&mut xml, "cac:ReceiverParty", "6", input.ose_ruc);

    xml.push_str("<cac:DocumentResponse><cac:Response>");
    element(&mut xml, "cbc:ReferenceID", serie_numero);
    xml.push_str(r#"<cbc:ResponseCode listAgencyName="PE:SUNAT">"#);
    xml.push_str(&escape(input.response_code));
    xml.push_str("</cbc:ResponseCode>");
    element(&mut xml, "cbc:Description", input.description);
    for note in input.notes {
        xml.push_str("<cac:Status>");
        xml.push_str(&format!(
            r#"<cbc:StatusReasonCode listURI="{RETURN_CODES_URI}">{}</cbc:StatusReasonCode>"#,
            escape(&note.code)
        ));
        element(&mut xml, "cbc:StatusReason", &note.message);
        xml.push_str("</cac:Status>");
    }
    xml.push_str("</cac:Response><cac:DocumentReference>");
    element(&mut xml, "cbc:ID", serie_numero);
    if let Some(date) = &facts.issue_date {
        element(&mut xml, "cbc:IssueDate", date);
    }
    if let Some(time) = &facts.issue_time {
        element(&mut xml, "cbc:IssueTime", time);
    }
    if let Some(tipo) = facts.tipo_comprobante() {
        element(&mut xml, "cbc:DocumentTypeCode", &tipo);
    }
    if let Some(digest) = &facts.digest_value {
        xml.push_str("<cac:Attachment><cac:ExternalReference>");
        element(&mut xml, "cbc:DocumentHash", digest);
        xml.push_str("</cac:ExternalReference></cac:Attachment>");
    }
    xml.push_str("</cac:DocumentReference>");
    party(&mut xml, "cac:IssuerParty", "6", issuer);
    if let (Some(tipo), Some(numero)) = (&facts.customer_tipo, &facts.customer_numero) {
        party(&mut xml, "cac:RecipientParty", tipo, numero);
    }
    xml.push_str("</cac:DocumentResponse></ApplicationResponse>");
    Ok(xml)
}

fn element(xml: &mut String, name: &str, text: &str) {
    xml.push('<');
    xml.push_str(name);
    xml.push('>');
    xml.push_str(&escape(text));
    xml.push_str("</");
    xml.push_str(name);
    xml.push('>');
}

fn party(xml: &mut String, name: &str, scheme_id: &str, company_id: &str) {
    xml.push_str(&format!(
        r#"<{name}><cac:PartyLegalEntity><cbc:CompanyID schemeID="{}" schemeAgencyName="PE:SUNAT" schemeURI="{CATALOG_6_URI}">{}</cbc:CompanyID></cac:PartyLegalEntity></{name}>"#,
        escape(scheme_id),
        escape(company_id)
    ));
}

pub fn escape(text: &str) -> String {
    let mut escaped = String::with_capacity(text.len());
    for c in text.chars() {
        match c {
            '&' => escaped.push_str("&amp;"),
            '<' => escaped.push_str("&lt;"),
            '>' => escaped.push_str("&gt;"),
            '"' => escaped.push_str("&quot;"),
            '\'' => escaped.push_str("&apos;"),
            c => escaped.push(c),
        }
    }
    escaped
}

/// A version-4-shaped UUID from the inputs: the same document, OSE and time
/// give the same CDR ID
fn derived_uuid(input: &CdrInput) -> String {
    let mut hasher = Sha256::new();
    for part in [
        input.ose_ruc,
        input.facts.supplier_ruc.as_deref().unwrap_or_default(),
        input.facts.id.as_deref().unwrap_or_default(),
        input.facts.digest_value.as_deref().unwrap_or_default(),
        &input.issued_at.to_rfc3339(),
    ] {
        hasher.update(part.as_bytes());
        hasher.update([0]);
    }
    let mut bytes: [u8; 16] = hasher.finalize()[..16].try_into().expect("16 bytes");
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    let hex: String = bytes.iter().map(|b| format!("{b:02x}")).collect();
    format!(
        "{}-{}-{}-{}-{}",
        &hex[0..8],
        &hex[8..12],
        &hex[12..16],
        &hex[16..20],
        &hex[20..32]
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::str::FromStr;
    use xhandler::prelude::CdrMetadata;

    fn facts() -> Facts {
        Facts {
            document_type: "Invoice".into(),
            id: Some("F001-1".into()),
            issue_date: Some("2026-11-15".into()),
            issue_time: Some("10:00:00".into()),
            type_code: Some("01".into()),
            supplier_ruc: Some("20123456789".into()),
            customer_tipo: Some("6".into()),
            customer_numero: Some("20999999991".into()),
            digest_value: Some("abc=".into()),
            ..Default::default()
        }
    }

    fn input<'a>(facts: &'a Facts, notes: &'a [Note]) -> CdrInput<'a> {
        CdrInput {
            facts,
            ose_ruc: "20100000001",
            sender_ruc: None,
            response_code: "0",
            description: "La Factura numero F001-1, ha sido aceptada",
            notes,
            issued_at: DateTime::parse_from_rfc3339("2026-11-16T15:30:00Z").unwrap(),
            received_at: None,
            id: None,
        }
    }

    #[test]
    fn builds_a_cdr_xsender_can_read() {
        let facts = facts();
        let notes = [Note {
            code: "4252".into(),
            message: "Observación <x>".into(),
        }];
        let xml = build(&input(&facts, &notes)).unwrap();
        // Lima time
        assert!(xml.contains("<cbc:ResponseDate>2026-11-16</cbc:ResponseDate><cbc:ResponseTime>10:30:00</cbc:ResponseTime>"));
        assert!(xml.contains("<cbc:DocumentHash>abc=</cbc:DocumentHash>"));
        assert!(xml.contains("Observación &lt;x&gt;"));
        let cdr = CdrMetadata::from_str(&xml).unwrap();
        assert_eq!(cdr.response_code, "0");
        assert_eq!(
            cdr.description,
            "La Factura numero F001-1, ha sido aceptada"
        );
        assert_eq!(cdr.notes, ["4252 - Observación <x>"]);
    }

    #[test]
    fn the_id_is_a_stable_uuid() {
        let facts = facts();
        let first = build(&input(&facts, &[])).unwrap();
        let second = build(&input(&facts, &[])).unwrap();
        assert_eq!(first, second);
        let id = &first[first.find("<cbc:ID>").unwrap() + 8..];
        let id = &id[..id.find('<').unwrap()];
        let shape = regex::Regex::new(
            r"^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
        )
        .unwrap();
        assert!(shape.is_match(id), "{id}");
    }

    #[test]
    fn needs_issuer_and_number() {
        let facts = Facts {
            supplier_ruc: None,
            ..facts()
        };
        assert!(build(&input(&facts, &[])).is_err());
    }
}
