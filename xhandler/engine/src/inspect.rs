//! Reads the data the service and the rules need from a UBL document in one
//! pass (quick-xml), without trusting it: DOCTYPEs are refused, so no DTD,
//! entity expansion or external fetch can happen. Elements are matched by
//! local name, so any namespace prefix is accepted.

use quick_xml::events::{BytesRef, BytesStart, Event};
use quick_xml::Reader;
use serde::Serialize;

/// Facts read from a document. Fields are `None` when the document has no
/// such element.
#[derive(Debug, Default, Clone, PartialEq)]
pub struct Facts {
    /// Root element: Invoice, CreditNote, SummaryDocuments...
    pub document_type: String,
    /// Root `cbc:ID`: serie-numero, or e.g. RC-20240115-1 for summaries
    pub id: Option<String>,
    pub issue_date: Option<String>,
    pub issue_time: Option<String>,
    /// InvoiceTypeCode / DespatchAdviceTypeCode (catalog 1)
    pub type_code: Option<String>,
    pub currency: Option<String>,
    /// PayableAmount of the monetary total; TotalInvoiceAmount for
    /// perception and retention
    pub total: Option<String>,
    pub supplier_ruc: Option<String>,
    /// Catalog 6 code of the buyer's (receiver's) identity document
    pub customer_tipo: Option<String>,
    pub customer_numero: Option<String>,
    /// Number of cac:BillingReference/cac:InvoiceDocumentReference (notes)
    pub billing_references: usize,
    /// cac:DiscrepancyResponse/cbc:ResponseCode (credit/debit note type)
    pub note_type: Option<String>,
    /// ds:DigestValue of the signature; `None` when unsigned
    pub digest_value: Option<String>,
    /// Document-level cac:TaxSubtotal entries: (catalog 5 tax code, taxable amount, tax amount)
    pub tax_subtotals: Vec<(String, String, String)>,
    /// TaxableAmount and TaxAmount of the cac:TaxSubtotal being read (parser state)
    pub pending_subtotal: (String, String),
    /// First cac:BillingReference of a note: (serie-numero, catalog 1 type)
    pub affected_id: Option<String>,
    pub affected_type: Option<String>,
    /// cac:PaymentTerms/cbc:PaymentMeansID of the FormaPago terms: Contado or Credito
    pub payment_means: Option<String>,
}

impl Facts {
    /// Catalog 1 code of the document; `None` for summaries and voided
    /// documents, which aren't comprobantes.
    pub fn tipo_comprobante(&self) -> Option<String> {
        let serie_starts = |prefix: char| {
            self.id
                .as_deref()
                .is_some_and(|id| id.to_ascii_uppercase().starts_with(prefix))
        };
        let code = match self.document_type.as_str() {
            "Invoice" => match self.type_code.as_deref() {
                Some(code) => return Some(code.to_string()),
                None if serie_starts('F') => "01",
                None if serie_starts('B') => "03",
                None => return None,
            },
            "CreditNote" => "07",
            "DebitNote" => "08",
            "DespatchAdvice" => match self.type_code.as_deref() {
                Some(code) => return Some(code.to_string()),
                None if serie_starts('V') => "31",
                None => "09",
            },
            "Retention" => "20",
            "Perception" => "40",
            _ => return None,
        };
        Some(code.to_string())
    }

    /// (serie, numero) of the ID: the numero is the part after the last `-`
    pub fn serie_numero(&self) -> (Option<String>, Option<u64>) {
        let Some(id) = self.id.as_deref() else {
            return (None, None);
        };
        match id.rsplit_once('-') {
            Some((serie, numero)) => (Some(serie.to_string()), numero.parse().ok()),
            None => (Some(id.to_string()), None),
        }
    }
}

const PREDEFINED_ENTITIES: [(&str, &str); 5] = [
    ("lt", "<"),
    ("gt", ">"),
    ("amp", "&"),
    ("apos", "'"),
    ("quot", "\""),
];

/// Appends a character reference or one of XML's predefined entities; any
/// other entity is an error (no DTD defines them)
pub fn resolve_reference(reference: &BytesRef, text: &mut String) -> Result<(), String> {
    if let Some(c) = reference.resolve_char_ref().map_err(|e| e.to_string())? {
        text.push(c);
        return Ok(());
    }
    let name = reference.decode().map_err(|e| e.to_string())?;
    let value = PREDEFINED_ENTITIES
        .iter()
        .find(|(entity, _)| *entity == name)
        .ok_or_else(|| format!("Entidad no definida: &{name};"))?
        .1;
    text.push_str(value);
    Ok(())
}

/// Reads the facts of a document. Fails on malformed XML and on any DOCTYPE.
pub fn read_facts(xml: &str) -> Result<Facts, String> {
    let mut reader = Reader::from_str(xml);
    let mut facts = Facts::default();
    // Local names from the root to the current element
    let mut path: Vec<String> = Vec::new();
    let mut text = String::new();
    // schemeID of the open customer cbc:ID / AdditionalAccountID
    let mut scheme_id: Option<String> = None;

    loop {
        match reader
            .read_event()
            .map_err(|e| format!("XML mal formado: {e}"))?
        {
            Event::DocType(_) => return Err("El XML no puede contener DOCTYPE".into()),
            Event::Start(start) => {
                open(&mut facts, &mut path, &start, &mut scheme_id)?;
                text.clear();
            }
            Event::Empty(start) => {
                open(&mut facts, &mut path, &start, &mut scheme_id)?;
                text.clear();
                close(&mut facts, &path, "", &mut scheme_id);
                path.pop();
            }
            Event::Text(t) => text.push_str(&t.xml10_content().map_err(|e| e.to_string())?),
            Event::CData(t) => text.push_str(&t.decode().map_err(|e| e.to_string())?),
            Event::GeneralRef(r) => resolve_reference(&r, &mut text)?,
            Event::End(_) => {
                close(&mut facts, &path, text.trim(), &mut scheme_id);
                text.clear();
                path.pop();
            }
            Event::Eof => break,
            _ => {}
        }
    }
    if facts.document_type.is_empty() {
        return Err("El XML no tiene elemento raíz".into());
    }
    Ok(facts)
}

fn open(
    facts: &mut Facts,
    path: &mut Vec<String>,
    start: &BytesStart,
    scheme_id: &mut Option<String>,
) -> Result<(), String> {
    let name = String::from_utf8_lossy(start.local_name().as_ref()).into_owned();
    if path.is_empty() {
        facts.document_type = name.clone();
    }
    if name == "InvoiceDocumentReference"
        && path
            .last()
            .is_some_and(|parent| parent == "BillingReference")
    {
        facts.billing_references += 1;
    }
    if name == "ID" && in_customer(path) {
        *scheme_id = start
            .try_get_attribute("schemeID")
            .map_err(|e| e.to_string())?
            .map(|a| String::from_utf8_lossy(&a.value).into_owned());
    }
    path.push(name);
    Ok(())
}

fn in_customer(path: &[String]) -> bool {
    path.iter().any(|e| {
        matches!(
            e.as_str(),
            "AccountingCustomerParty" | "ReceiverParty" | "DeliveryCustomerParty"
        )
    })
}

fn close(facts: &mut Facts, path: &[String], text: &str, scheme_id: &mut Option<String>) {
    let set = |slot: &mut Option<String>| {
        if slot.is_none() && !text.is_empty() {
            *slot = Some(text.to_string());
        }
    };
    let names: Vec<&str> = path.iter().map(String::as_str).collect();
    let inside = |name: &str| names.contains(&name);
    match names.as_slice() {
        [_, "ID"] => set(&mut facts.id),
        [_, "IssueDate"] => set(&mut facts.issue_date),
        [_, "IssueTime"] => set(&mut facts.issue_time),
        [_, "InvoiceTypeCode" | "DespatchAdviceTypeCode"] => set(&mut facts.type_code),
        [_, "DocumentCurrencyCode"] => set(&mut facts.currency),
        [_, "LegalMonetaryTotal" | "RequestedMonetaryTotal", "PayableAmount"]
        | [_, "TotalInvoiceAmount"] => set(&mut facts.total),
        [_, "DiscrepancyResponse", "ResponseCode"] => set(&mut facts.note_type),
        [_, "BillingReference", "InvoiceDocumentReference", "ID"] => set(&mut facts.affected_id),
        [_, "PaymentTerms", "PaymentMeansID"]
            if text.eq_ignore_ascii_case("contado") || text.eq_ignore_ascii_case("credito") =>
        {
            set(&mut facts.payment_means)
        }
        [_, "BillingReference", "InvoiceDocumentReference", "DocumentTypeCode"] => {
            set(&mut facts.affected_type)
        }
        [_, "TaxTotal", "TaxSubtotal", "TaxableAmount"] => {
            facts.pending_subtotal.0 = text.to_string()
        }
        [_, "TaxTotal", "TaxSubtotal", "TaxAmount"] => facts.pending_subtotal.1 = text.to_string(),
        [_, "TaxTotal", "TaxSubtotal", "TaxCategory", "TaxScheme", "ID"] => {
            let (taxable, amount) = std::mem::take(&mut facts.pending_subtotal);
            facts
                .tax_subtotals
                .push((text.to_string(), taxable, amount));
        }
        [.., "DigestValue"] if inside("Signature") => set(&mut facts.digest_value),
        [.., "ID" | "CustomerAssignedAccountID"]
            if inside("AccountingSupplierParty")
                || inside("AgentParty")
                || inside("DespatchSupplierParty") =>
        {
            set(&mut facts.supplier_ruc)
        }
        // UBL 2.1: cac:PartyIdentification/cbc:ID@schemeID
        [.., "PartyIdentification", "ID"] if in_customer(path) => {
            if facts.customer_numero.is_none() {
                set(&mut facts.customer_numero);
                facts.customer_tipo = scheme_id.take().or(facts.customer_tipo.take());
            }
        }
        // UBL 2.0 (summaries): CustomerAssignedAccountID + AdditionalAccountID
        [.., "CustomerAssignedAccountID"] if in_customer(path) => set(&mut facts.customer_numero),
        [.., "AdditionalAccountID"] if in_customer(path) => set(&mut facts.customer_tipo),
        _ => {}
    }
}

// --- POST /v1/inspect --------------------------------------------------------

#[derive(Serialize, Debug, PartialEq)]
pub struct InspectResponse {
    pub document_type: String,
    pub tipo_comprobante: Option<String>,
    pub ruc: Option<String>,
    pub serie: Option<String>,
    pub numero: Option<u64>,
    pub serie_numero: Option<String>,
    pub fecha_emision: Option<String>,
    pub moneda: Option<String>,
    pub total: Option<String>,
    pub customer_tipo: Option<String>,
    pub customer_numero: Option<String>,
    pub digest_value: Option<String>,
    pub signed: bool,
    /// Lowercase hex SHA-256 of the bytes sent to SUNAT
    pub sha256: String,
    /// Amounts by tax (registers of sales and purchases, §11.9)
    pub amounts: Amounts,
    pub affected_serie_numero: Option<String>,
    pub affected_tipo: Option<String>,
    /// contado or credito (facturas); `None` when the document doesn't say
    pub payment_terms: Option<String>,
}

/// Document-level amounts by tax (catalog 5 codes), as decimal strings; `None` when absent
#[derive(Serialize, Debug, PartialEq, Default)]
pub struct Amounts {
    /// 1000: base gravada
    pub gravada: Option<String>,
    pub igv: Option<String>,
    /// 9997
    pub exonerada: Option<String>,
    /// 9998
    pub inafecta: Option<String>,
    /// 9995
    pub exportacion: Option<String>,
    /// 9996: gratuitas
    pub gratuita: Option<String>,
    /// 1016: base IVAP and the IVAP
    pub ivap_base: Option<String>,
    pub ivap: Option<String>,
    /// 2000
    pub isc: Option<String>,
    /// 7152
    pub icbper: Option<String>,
    /// 9999
    pub otros: Option<String>,
}

fn amounts(subtotals: &[(String, String, String)]) -> Amounts {
    let mut a = Amounts::default();
    let some = |s: &str| (!s.is_empty()).then(|| s.to_string());
    for (code, taxable, amount) in subtotals {
        match code.as_str() {
            "1000" => {
                a.gravada = some(taxable);
                a.igv = some(amount);
            }
            "1016" => {
                a.ivap_base = some(taxable);
                a.ivap = some(amount);
            }
            "9997" => a.exonerada = some(taxable),
            "9998" => a.inafecta = some(taxable),
            "9995" => a.exportacion = some(taxable),
            "9996" => a.gratuita = some(taxable),
            "2000" => a.isc = some(amount),
            "7152" => a.icbper = some(amount),
            "9999" => a.otros = some(amount),
            _ => {}
        }
    }
    a
}

pub fn inspect(xml: &str) -> Result<InspectResponse, String> {
    let facts = read_facts(xml)?;
    let (serie, numero) = facts.serie_numero();
    let sha256 = xhandler::prelude::UblFile::from(xml).sha256();
    Ok(InspectResponse {
        tipo_comprobante: facts.tipo_comprobante(),
        ruc: facts.supplier_ruc.clone(),
        serie,
        numero,
        serie_numero: facts.id.clone(),
        fecha_emision: facts.issue_date.clone(),
        moneda: facts.currency.clone(),
        total: facts.total.clone(),
        customer_tipo: facts.customer_tipo.clone(),
        customer_numero: facts.customer_numero.clone(),
        signed: facts.digest_value.is_some(),
        amounts: amounts(&facts.tax_subtotals),
        affected_serie_numero: facts.affected_id.clone(),
        affected_tipo: facts.affected_type.clone(),
        payment_terms: facts.payment_means.as_ref().map(|m| m.to_lowercase()),
        digest_value: facts.digest_value,
        document_type: facts.document_type,
        sha256,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    const INVOICE: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2" xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2" xmlns:ext="urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2" xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
  <ext:UBLExtensions><ext:UBLExtension><ext:ExtensionContent><ds:Signature><ds:SignedInfo><ds:Reference><ds:DigestValue>abc=</ds:DigestValue></ds:Reference></ds:SignedInfo></ds:Signature></ext:ExtensionContent></ext:UBLExtension></ext:UBLExtensions>
  <cbc:ID>F001-00000042</cbc:ID>
  <cbc:IssueDate>2026-11-15</cbc:IssueDate>
  <cbc:InvoiceTypeCode listID="0101">01</cbc:InvoiceTypeCode>
  <cbc:DocumentCurrencyCode>PEN</cbc:DocumentCurrencyCode>
  <cac:Signature><cbc:ID>12345678912</cbc:ID></cac:Signature>
  <cac:AccountingSupplierParty><cac:Party><cac:PartyIdentification><cbc:ID schemeID="6">12345678912</cbc:ID></cac:PartyIdentification><cac:PartyLegalEntity><cbc:RegistrationName>A &amp; B &#233;</cbc:RegistrationName></cac:PartyLegalEntity></cac:Party></cac:AccountingSupplierParty>
  <cac:AccountingCustomerParty><cac:Party><cac:PartyIdentification><cbc:ID schemeID="6">12121212121</cbc:ID></cac:PartyIdentification></cac:Party></cac:AccountingCustomerParty>
  <cac:LegalMonetaryTotal><cbc:PayableAmount currencyID="PEN">1180.00</cbc:PayableAmount></cac:LegalMonetaryTotal>
</Invoice>"#;

    #[test]
    fn reads_invoice_facts() {
        let facts = read_facts(INVOICE).unwrap();
        assert_eq!(facts.document_type, "Invoice");
        assert_eq!(facts.id.as_deref(), Some("F001-00000042"));
        assert_eq!(facts.tipo_comprobante().as_deref(), Some("01"));
        assert_eq!(facts.supplier_ruc.as_deref(), Some("12345678912"));
        assert_eq!(facts.customer_tipo.as_deref(), Some("6"));
        assert_eq!(facts.customer_numero.as_deref(), Some("12121212121"));
        assert_eq!(facts.total.as_deref(), Some("1180.00"));
        assert_eq!(facts.digest_value.as_deref(), Some("abc="));
        assert_eq!(facts.serie_numero(), (Some("F001".to_string()), Some(42)));
    }

    #[test]
    fn inspect_response() {
        let response = inspect(INVOICE).unwrap();
        assert_eq!(response.serie_numero.as_deref(), Some("F001-00000042"));
        assert_eq!(response.fecha_emision.as_deref(), Some("2026-11-15"));
        assert_eq!(response.moneda.as_deref(), Some("PEN"));
        assert!(response.signed);
        assert_eq!(response.sha256.len(), 64);
    }

    #[test]
    fn summaries_have_no_tipo_comprobante() {
        let xml = r#"<SummaryDocuments xmlns:cbc="urn:cbc" xmlns:cac="urn:cac" xmlns:sac="urn:sac"><cbc:ID>RC-20240115-1</cbc:ID><cac:AccountingSupplierParty><cbc:CustomerAssignedAccountID>12345678912</cbc:CustomerAssignedAccountID></cac:AccountingSupplierParty></SummaryDocuments>"#;
        let facts = read_facts(xml).unwrap();
        assert_eq!(facts.tipo_comprobante(), None);
        assert_eq!(facts.supplier_ruc.as_deref(), Some("12345678912"));
        assert_eq!(
            facts.serie_numero(),
            (Some("RC-20240115".to_string()), Some(1))
        );
        assert!(!inspect(xml).unwrap().signed);
    }

    #[test]
    fn refuses_doctype_and_unknown_entities() {
        let doctype = r#"<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><Invoice><ID>&e;</ID></Invoice>"#;
        assert!(read_facts(doctype).unwrap_err().contains("DOCTYPE"));
        assert!(read_facts("<Invoice><ID>&e;</ID></Invoice>").is_err());
        assert!(read_facts("<Invoice><ID></Invoice>").is_err());
        assert!(read_facts("").is_err());
    }

    #[test]
    fn counts_note_references() {
        let xml = r#"<CreditNote><cbc:ID xmlns:cbc="u">FC01-1</cbc:ID><BillingReference><InvoiceDocumentReference/></BillingReference><BillingReference><InvoiceDocumentReference/></BillingReference><DiscrepancyResponse><ResponseCode>01</ResponseCode></DiscrepancyResponse></CreditNote>"#;
        let facts = read_facts(xml).unwrap();
        assert_eq!(facts.billing_references, 2);
        assert_eq!(facts.note_type.as_deref(), Some("01"));
        assert_eq!(facts.tipo_comprobante().as_deref(), Some("07"));
    }
    #[test]
    fn amounts_by_tax_and_the_affected_document() {
        let xml = r#"<CreditNote xmlns="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2" xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
  <cbc:ID>FC01-7</cbc:ID>
  <cac:BillingReference><cac:InvoiceDocumentReference><cbc:ID>F001-3</cbc:ID><cbc:DocumentTypeCode>01</cbc:DocumentTypeCode></cac:InvoiceDocumentReference></cac:BillingReference>
  <cac:TaxTotal><cbc:TaxAmount currencyID="PEN">18.00</cbc:TaxAmount>
    <cac:TaxSubtotal><cbc:TaxableAmount currencyID="PEN">100.00</cbc:TaxableAmount><cbc:TaxAmount currencyID="PEN">18.00</cbc:TaxAmount><cac:TaxCategory><cac:TaxScheme><cbc:ID>1000</cbc:ID></cac:TaxScheme></cac:TaxCategory></cac:TaxSubtotal>
    <cac:TaxSubtotal><cbc:TaxableAmount currencyID="PEN">50.00</cbc:TaxableAmount><cbc:TaxAmount currencyID="PEN">0.00</cbc:TaxAmount><cac:TaxCategory><cac:TaxScheme><cbc:ID>9997</cbc:ID></cac:TaxScheme></cac:TaxCategory></cac:TaxSubtotal>
  </cac:TaxTotal>
  <cac:CreditNoteLine><cac:TaxTotal><cac:TaxSubtotal><cbc:TaxableAmount>999</cbc:TaxableAmount><cac:TaxCategory><cac:TaxScheme><cbc:ID>1000</cbc:ID></cac:TaxScheme></cac:TaxCategory></cac:TaxSubtotal></cac:TaxTotal></cac:CreditNoteLine>
</CreditNote>"#;
        let response = inspect(xml).unwrap();
        assert_eq!(response.amounts.gravada.as_deref(), Some("100.00"));
        assert_eq!(response.amounts.igv.as_deref(), Some("18.00"));
        assert_eq!(response.amounts.exonerada.as_deref(), Some("50.00"));
        assert_eq!(response.amounts.inafecta, None);
        assert_eq!(response.affected_serie_numero.as_deref(), Some("F001-3"));
        assert_eq!(response.affected_tipo.as_deref(), Some("01"));
        let credit = r#"<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2" xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"><cbc:ID>F001-1</cbc:ID><cac:PaymentTerms><cbc:ID>FormaPago</cbc:ID><cbc:PaymentMeansID>Credito</cbc:PaymentMeansID></cac:PaymentTerms><cac:PaymentTerms><cbc:ID>FormaPago</cbc:ID><cbc:PaymentMeansID>Cuota001</cbc:PaymentMeansID></cac:PaymentTerms></Invoice>"#;
        assert_eq!(
            inspect(credit).unwrap().payment_terms.as_deref(),
            Some("credito")
        );
    }
}
