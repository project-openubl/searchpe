//! The fake's SIRE and factura negociable conformity APIs (DESIGN.md §11.7,
//! §11.8): proposals are built from the documents the fake received, so a
//! round trip (proposal → reconciliation → replacement) can run end to end.
//!
//! - RVIE proposal: the accepted documents issued by the token's RUC in the period.
//! - RCE proposal: the accepted documents addressed to the token's RUC.
//! - Replacements are checked (the ZIP's SHA-256 must match the `hash` field),
//!   counted and listed at `GET /fake/sire`.
//! - Conformity comes from a `xhandler-fake-conformity:<agreed|objected|silent>`
//!   marker in the XML; pending otherwise.

use std::collections::HashMap;
use std::io::{Cursor, Read, Write};

use axum::body::Bytes;
use axum::extract::{Path, Query, State};
use axum::http::{header, HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::Json;
use regex::Regex;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

use super::{hex, key_of, Fake};

const CONFORMITY_MARKER: &str = "xhandler-fake-conformity:";

/// RVIE proposal columns (SIRE manual, annex 3: "Estructura de la propuesta del RVIE")
pub const RVIE_HEADER: &str = "RUC|Apellidos y Nombres o Razón social|Periodo|CAR SUNAT|Fecha de emisión|Fecha Vcto/Pago|Tipo CP/Doc.|Serie del CDP|Nro CP o Doc. Nro Inicial (Rango)|Nro Final (Rango)|Tipo Doc Identidad|Nro Doc Identidad|Apellidos Nombres/ Razón Social|Valor Facturado Exportación|BI Gravada|Dscto BI|IGV / IPM|Dscto IGV / IPM|Mto Exonerado|Mto Inafecto|ISC|BI Grav IVAP|IVAP|ICBPER|Otros Tributos|Total CP|Moneda|Tipo Cambio|Fecha Emisión Doc Modificado|Tipo CP Modificado|Serie CP Modificado|Nro CP Modificado";
/// RCE proposal columns (annex 4: "Estructura de la propuesta del RCE")
pub const RCE_HEADER: &str = "RUC|Apellidos y Nombres o Razón social|Periodo|CAR SUNAT|Fecha de emisión|Fecha Vcto/Pago|Tipo CP/Doc.|Serie del CDP|Año|Nro CP o Doc. Nro Inicial (Rango)|Nro Final (Rango)|Tipo Doc Identidad|Nro Doc Identidad|Apellidos Nombres/ Razón Social|BI Gravado DG|IGV / IPM DG|BI Gravado DGNG|IGV / IPM DGNG|BI Gravado DNG|IGV / IPM DNG|Valor Adq. NG|ISC|ICBPER|Otros Trib/ Cargos|Total CP|Moneda|Tipo de Cambio";

/// What the fake keeps of a received document for SIRE and conformity
#[derive(Clone, Serialize, Default, Debug)]
pub struct Facts {
    issue_date: String,
    currency: String,
    taxable: String,
    igv: String,
    total: String,
    customer_tipo: String,
    customer_numero: String,
    customer_name: String,
    conformity: String,
    #[serde(skip)]
    accepted: bool,
}

fn first(text: &str, pattern: &str) -> String {
    Regex::new(pattern)
        .ok()
        .and_then(|re| re.captures(text))
        .and_then(|c| c.get(1))
        .map(|m| m.as_str().trim().to_string())
        .unwrap_or_default()
}

impl Facts {
    pub fn of(text: &str, accepted: bool) -> Self {
        let conformity = text
            .find(CONFORMITY_MARKER)
            .map(|start| {
                text[start + CONFORMITY_MARKER.len()..]
                    .chars()
                    .take_while(|c| c.is_ascii_lowercase())
                    .collect::<String>()
            })
            .unwrap_or_else(|| "pending".into());
        let customer = first(
            text,
            r"(?s)<cac:AccountingCustomerParty>(.*?)</cac:AccountingCustomerParty>",
        );
        Self {
            issue_date: first(text, r"<cbc:IssueDate>([^<]+)</cbc:IssueDate>"),
            currency: first(text, r#"<cbc:PayableAmount[^>]*currencyID="([A-Z]{3})""#),
            taxable: first(
                text,
                r"(?s)<cac:LegalMonetaryTotal>.*?<cbc:LineExtensionAmount[^>]*>([^<]+)<",
            ),
            igv: first(text, r"(?s)<cac:TaxTotal>\s*<cbc:TaxAmount[^>]*>([^<]+)<"),
            total: first(text, r"<cbc:PayableAmount[^>]*>([^<]+)</cbc:PayableAmount>"),
            customer_tipo: first(&customer, r#"<cbc:ID[^>]*schemeID="([^"]+)""#),
            customer_numero: first(&customer, r"<cbc:ID[^>]*>([^<]+)</cbc:ID>"),
            customer_name: first(
                &customer,
                r"<cbc:RegistrationName>(?:<!\[CDATA\[)?([^<\]]+)",
            ),
            conformity,
            accepted,
        }
    }
}

#[derive(Default)]
pub struct SireState {
    tickets: HashMap<String, SireTicket>,
    files: HashMap<String, Vec<u8>>,
    submitted: Vec<Submitted>,
    next: u64,
}

struct SireTicket {
    files: Vec<String>,
}

#[derive(Clone, Serialize)]
pub struct Submitted {
    ruc: String,
    book: String,
    period: String,
    file_name: String,
    rows: usize,
    sha256: String,
}

fn unauthorized() -> Response {
    (
        StatusCode::UNAUTHORIZED,
        Json(serde_json::json!({ "cod": "401", "msg": "No autorizado" })),
    )
        .into_response()
}

/// The RUC behind a bearer token (its SOL username starts with it)
fn ruc_of(fake: &Fake, headers: &HeaderMap) -> Option<String> {
    let token = headers
        .get(header::AUTHORIZATION)?
        .to_str()
        .ok()?
        .strip_prefix("Bearer ")?
        .to_string();
    let username = fake.inner().tokens.get(&token).cloned()?;
    username.get(..11).map(str::to_owned)
}

fn dmy(iso: &str) -> String {
    let parts: Vec<&str> = iso.split('-').collect();
    match parts.as_slice() {
        [y, m, d] => format!("{d}/{m}/{y}"),
        _ => iso.to_string(),
    }
}

fn or_zero(value: &str) -> &str {
    if value.is_empty() {
        "0.00"
    } else {
        value
    }
}

fn zip(name: &str, content: &str) -> Vec<u8> {
    let mut zip = zip::ZipWriter::new(Cursor::new(Vec::new()));
    let options = zip::write::SimpleFileOptions::default();
    let written = (|| -> zip::result::ZipResult<Vec<u8>> {
        zip.start_file(name, options)?;
        zip.write_all(content.as_bytes())?;
        Ok(zip.finish()?.into_inner())
    })();
    written.expect("zips in memory")
}

fn new_ticket(fake: &Fake, files: Vec<(String, Vec<u8>)>) -> String {
    let mut inner = fake.inner();
    inner.sire.next += 1;
    let ticket = format!("2027{:08}", inner.sire.next);
    let names = files.iter().map(|(n, _)| n.clone()).collect();
    for (name, bytes) in files {
        inner.sire.files.insert(name, bytes);
    }
    inner
        .sire
        .tickets
        .insert(ticket.clone(), SireTicket { files: names });
    ticket
}

fn proposal(fake: &Fake, headers: &HeaderMap, period: &str, sales: bool) -> Response {
    let Some(ruc) = ruc_of(fake, headers) else {
        return unauthorized();
    };
    let month = format!(
        "{}-{}",
        &period[..4.min(period.len())],
        period.get(4..6).unwrap_or("")
    );
    let rows: Vec<String> = {
        let inner = fake.inner();
        inner
            .received
            .values()
            .filter(|r| r.facts.accepted && r.facts.issue_date.starts_with(&month))
            .filter_map(|r| {
                let parts: Vec<&str> = r.key.split('-').collect();
                let [issuer, tipo, serie, numero] = parts.as_slice() else {
                    return None;
                };
                if !["01", "03", "07", "08"].contains(tipo) {
                    return None;
                }
                let f = &r.facts;
                // SIRE reports credit notes with negative amounts
                let sign = |v: &str| {
                    let v = or_zero(v);
                    if *tipo == "07" && !v.starts_with('-') {
                        format!("-{v}")
                    } else {
                        v.to_string()
                    }
                };
                if sales && *issuer == ruc {
                    Some(format!(
                        "{ruc}|EMPRESA {ruc}|{period}||{date}||{tipo}|{serie}|{numero}||{ct}|{cn}|{name}|0.00|{bi}|0.00|{igv}|0.00|0.00|0.00|0.00|0.00|0.00|0.00|0.00|{total}|{cur}|||||",
                        date = dmy(&f.issue_date),
                        ct = f.customer_tipo,
                        cn = f.customer_numero,
                        name = f.customer_name,
                        bi = sign(&f.taxable),
                        igv = sign(&f.igv),
                        total = sign(&f.total),
                        cur = f.currency,
                    ))
                } else if !sales && f.customer_numero == ruc {
                    Some(format!(
                        "{ruc}|EMPRESA {ruc}|{period}||{date}||{tipo}|{serie}||{numero}||6|{issuer}|PROVEEDOR {issuer}|{bi}|{igv}|0.00|0.00|0.00|0.00|0.00|0.00|0.00|0.00|{total}|{cur}|",
                        date = dmy(&f.issue_date),
                        bi = sign(&f.taxable),
                        igv = sign(&f.igv),
                        total = sign(&f.total),
                        cur = f.currency,
                    ))
                } else {
                    None
                }
            })
            .collect()
    };
    let (book, header) = if sales {
        ("140400", RVIE_HEADER)
    } else {
        ("080400", RCE_HEADER)
    };
    let base = format!(
        "LE{ruc}{period}00{book}02{}",
        if sales { "1112" } else { "2112" }
    );
    let mut text = String::from(header);
    for row in rows {
        text.push('\n');
        text.push_str(&row);
    }
    let name = format!("{base}.zip");
    let ticket = new_ticket(fake, vec![(name, zip(&format!("{base}.txt"), &text))]);
    Json(serde_json::json!({ "numTicket": ticket })).into_response()
}

pub async fn export_rvie(
    State(fake): State<Fake>,
    headers: HeaderMap,
    Path(period): Path<String>,
) -> Response {
    if let Some(response) = fake.gate().await {
        return response;
    }
    proposal(&fake, &headers, &period, true)
}

pub async fn export_rce(
    State(fake): State<Fake>,
    headers: HeaderMap,
    Path(period): Path<String>,
) -> Response {
    if let Some(response) = fake.gate().await {
        return response;
    }
    proposal(&fake, &headers, &period, false)
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TicketQuery {
    num_ticket: String,
}

pub async fn ticket_state(
    State(fake): State<Fake>,
    headers: HeaderMap,
    Query(query): Query<TicketQuery>,
) -> Response {
    if let Some(response) = fake.gate().await {
        return response;
    }
    if ruc_of(&fake, &headers).is_none() {
        return unauthorized();
    }
    let inner = fake.inner();
    let Some(ticket) = inner.sire.tickets.get(&query.num_ticket) else {
        return Json(serde_json::json!({ "registros": [] })).into_response();
    };
    let files: Vec<_> = ticket
        .files
        .iter()
        .map(|name| serde_json::json!({ "codTipoAchivoReporte": "01", "nomArchivoReporte": name }))
        .collect();
    Json(serde_json::json!({ "registros": [{
        "numTicket": query.num_ticket,
        "codEstadoProceso": "06",
        "desEstadoProceso": "Terminado",
        "archivoReporte": files,
    }]}))
    .into_response()
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FileQuery {
    nom_archivo_reporte: String,
}

pub async fn report_file(
    State(fake): State<Fake>,
    headers: HeaderMap,
    Query(query): Query<FileQuery>,
) -> Response {
    if ruc_of(&fake, &headers).is_none() {
        return unauthorized();
    }
    match fake.inner().sire.files.get(&query.nom_archivo_reporte) {
        Some(bytes) => (
            [(header::CONTENT_TYPE, "application/octet-stream")],
            bytes.clone(),
        )
            .into_response(),
        None => StatusCode::NOT_FOUND.into_response(),
    }
}

/// The parts of a multipart body: (name, bytes)
fn parts(content_type: &str, body: &[u8]) -> Vec<(String, Vec<u8>)> {
    let Some(boundary) = content_type.split("boundary=").nth(1) else {
        return vec![];
    };
    let delimiter = format!("--{}", boundary.trim_matches('"'));
    let text = body;
    let mut out = Vec::new();
    let positions: Vec<usize> = find_all(text, delimiter.as_bytes());
    for window in positions.windows(2) {
        let part = &text[window[0] + delimiter.len()..window[1]];
        let Some(split) = find_all(part, b"\r\n\r\n").first().copied() else {
            continue;
        };
        let head = String::from_utf8_lossy(&part[..split]);
        let Some(name) = head
            .split("name=\"")
            .nth(1)
            .and_then(|rest| rest.split('"').next())
        else {
            continue;
        };
        let mut content = &part[split + 4..];
        if content.ends_with(b"\r\n") {
            content = &content[..content.len() - 2];
        }
        out.push((name.to_string(), content.to_vec()));
    }
    out
}

fn find_all(haystack: &[u8], needle: &[u8]) -> Vec<usize> {
    if needle.is_empty() || haystack.len() < needle.len() {
        return vec![];
    }
    (0..=haystack.len() - needle.len())
        .filter(|&i| &haystack[i..i + needle.len()] == needle)
        .collect()
}

pub async fn replace(
    State(fake): State<Fake>,
    headers: HeaderMap,
    Path((book, period)): Path<(String, String)>,
    body: Bytes,
) -> Response {
    if let Some(response) = fake.gate().await {
        return response;
    }
    let Some(ruc) = ruc_of(&fake, &headers) else {
        return unauthorized();
    };
    let content_type = headers
        .get(header::CONTENT_TYPE)
        .and_then(|v| v.to_str().ok())
        .unwrap_or_default();
    let parts = parts(content_type, &body);
    let get = |name: &str| {
        parts
            .iter()
            .find(|(n, _)| n == name)
            .map(|(_, b)| b.clone())
    };
    let (Some(zip_bytes), Some(hash), Some(file_name)) =
        (get("archivo"), get("hash"), get("nomArchivo"))
    else {
        return (
            StatusCode::BAD_REQUEST,
            Json(serde_json::json!({ "cod": "1001", "msg": "Faltan el archivo, su nombre o su hash" })),
        )
            .into_response();
    };
    let sha256 = hex(&Sha256::digest(&zip_bytes));
    if String::from_utf8_lossy(&hash) != sha256 {
        return (
            StatusCode::BAD_REQUEST,
            Json(serde_json::json!({ "cod": "1002", "msg": "El hash no corresponde al archivo" })),
        )
            .into_response();
    }
    let mut text = String::new();
    let readable = zip::ZipArchive::new(Cursor::new(zip_bytes))
        .ok()
        .and_then(|mut archive| {
            let mut file = archive.by_index(0).ok()?;
            file.read_to_string(&mut text).ok()
        });
    if readable.is_none() {
        return (
            StatusCode::BAD_REQUEST,
            Json(serde_json::json!({ "cod": "1003", "msg": "El archivo ZIP esta corrupto" })),
        )
            .into_response();
    }
    let rows = text
        .lines()
        .skip(1)
        .filter(|l| !l.trim().is_empty())
        .count();
    fake.inner().sire.submitted.push(Submitted {
        ruc,
        book,
        period,
        file_name: String::from_utf8_lossy(&file_name).into_owned(),
        rows,
        sha256,
    });
    let ticket = new_ticket(&fake, vec![]);
    Json(serde_json::json!({ "numTicket": ticket })).into_response()
}

pub async fn submitted(State(fake): State<Fake>) -> Json<Vec<Submitted>> {
    Json(fake.inner().sire.submitted.clone())
}

pub async fn conformity(
    State(fake): State<Fake>,
    headers: HeaderMap,
    Path(key): Path<String>,
) -> Response {
    if let Some(response) = fake.gate().await {
        return response;
    }
    if ruc_of(&fake, &headers).is_none() {
        return unauthorized();
    }
    let Some(key) = key_of(&key) else {
        return StatusCode::NOT_FOUND.into_response();
    };
    let inner = fake.inner();
    match inner.received.get(&key) {
        None => (
            StatusCode::NOT_FOUND,
            Json(serde_json::json!({ "estado": "0", "descripcion": "El comprobante no existe" })),
        )
            .into_response(),
        Some(received) => {
            let (estado, descripcion) = match received.facts.conformity.as_str() {
                "agreed" => ("2", "Conforme"),
                "objected" => ("3", "Disconforme"),
                "silent" => ("4", "Conformidad presunta"),
                _ => ("1", "Pendiente de conformidad"),
            };
            Json(serde_json::json!({ "estado": estado, "descripcion": descripcion }))
                .into_response()
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn facts_are_read_from_the_xml() {
        let xml = r#"<Invoice><cbc:IssueDate>2027-01-15</cbc:IssueDate><cac:AccountingCustomerParty><cac:Party><cac:PartyIdentification><cbc:ID schemeID="6">20111111111</cbc:ID></cac:PartyIdentification><cac:PartyLegalEntity><cbc:RegistrationName><![CDATA[CLIENTE SAC]]></cbc:RegistrationName></cac:PartyLegalEntity></cac:Party></cac:AccountingCustomerParty><cac:TaxTotal><cbc:TaxAmount currencyID="PEN">18.00</cbc:TaxAmount></cac:TaxTotal><cac:LegalMonetaryTotal><cbc:LineExtensionAmount currencyID="PEN">100.00</cbc:LineExtensionAmount><cbc:PayableAmount currencyID="PEN">118.00</cbc:PayableAmount></cac:LegalMonetaryTotal><!-- xhandler-fake-conformity:objected --></Invoice>"#;
        let facts = Facts::of(xml, true);
        assert_eq!(facts.issue_date, "2027-01-15");
        assert_eq!(facts.customer_numero, "20111111111");
        assert_eq!(facts.customer_tipo, "6");
        assert_eq!(facts.customer_name, "CLIENTE SAC");
        assert_eq!(facts.igv, "18.00");
        assert_eq!(facts.taxable, "100.00");
        assert_eq!(facts.total, "118.00");
        assert_eq!(facts.currency, "PEN");
        assert_eq!(facts.conformity, "objected");
    }

    #[test]
    fn multipart_parts_are_split() {
        let body = b"--b\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"x.zip\"\r\n\r\nPK\r\n--b\r\nContent-Disposition: form-data; name=\"hash\"\r\n\r\nabc\r\n--b--\r\n";
        let parts = parts("multipart/form-data; boundary=b", body);
        assert_eq!(parts.len(), 2);
        assert_eq!(parts[0], ("archivo".to_string(), b"PK".to_vec()));
        assert_eq!(parts[1], ("hash".to_string(), b"abc".to_vec()));
    }
}
