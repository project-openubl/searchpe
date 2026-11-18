//! OSE role: forwarding to SUNAT (DESIGN.md §11.4 step 6; RS 117-2017 art.
//! 7.5, Annex C §IV). Within 1 hour of issuing a CDR-OSE, the OSE sends SUNAT
//! the issuer's document and that CDR: one ZIP with both XML files
//! (`RUC-TT-SERIE-NUMERO.xml` and `R-RUC-TT-SERIE-NUMERO.xml`) through
//! `sendBill`, authenticated with the OSE's own SUNAT user (WS-Security
//! UsernameToken). SUNAT answers with an acknowledgement (`applicationResponse`)
//! or a fault.
//!
//! SUNAT's OSE domains (e-ose1/e-ose2, e-betaose1/e-betaose2) are given to the
//! OSE during its registration tests and reached only from its declared IPs,
//! so the URL comes from the app's configuration (`url`); the fake stands in
//! for them until then.
//!
//! Outcomes, as for `/v1/send`: `accepted` (acknowledged), `rejected`
//! (SUNAT refused it: a permanent code), `unknown` (no usable answer or a
//! transient code: retry, SUNAT may have it), `not_delivered` (never reached
//! SUNAT).

use std::io::{Cursor, Read, Write};
use std::sync::Arc;
use std::time::Duration;

use axum::extract::{Json, State};
use base64::{engine::general_purpose::STANDARD, Engine as _};
use regex::Regex;
use serde::{Deserialize, Serialize};
use tracing::Instrument;

use super::sunat::Environment;
use super::{ApiError, ApiJson, Config, ErrorKind};
use crate::cdr::escape;
use crate::codes::{self, CodeClass};

/// Below the app's read timeout for the engine (timeouts nest)
const TIMEOUT: Duration = Duration::from_secs(30);

#[derive(Deserialize)]
pub struct OseCredentials {
    pub username: String,
    pub password: String,
}

#[derive(Deserialize)]
pub struct ForwardRequest {
    pub environment: Environment,
    /// SUNAT's OSE billService; required outside the fake
    pub url: Option<String>,
    pub credentials: OseCredentials,
    /// RUC-TT-SERIE-NUMERO, without extension
    pub name: String,
    /// The issuer's signed document
    pub xml: String,
    /// The signed CDR-OSE
    pub cdr_xml: String,
    /// `send_bill` (comprobantes) or `send_summary` (resúmenes and comunicaciones de baja, answered with a ticket)
    #[serde(default)]
    pub operation: Operation,
}

#[derive(Deserialize, Default, Clone, Copy, PartialEq, Debug)]
#[serde(rename_all = "snake_case")]
pub enum Operation {
    #[default]
    SendBill,
    SendSummary,
}

#[derive(Serialize, Debug, PartialEq, Clone, Copy)]
#[serde(rename_all = "snake_case")]
pub enum ForwardStatus {
    Accepted,
    /// SUNAT refused the document itself (2000–3999): a person must look at it
    Rejected,
    Unknown,
    NotDelivered,
    /// SUNAT refused the request for a reason that isn't the document (credentials, the
    /// package, an unclassified code, 1032/1033 whose digest isn't compared here): keep it
    /// queued and stop until a person fixes it
    Blocked,
}

#[derive(Serialize, Debug)]
pub struct ForwardResponse {
    pub status: ForwardStatus,
    pub code: Option<String>,
    pub description: Option<String>,
}

pub(super) async fn forward(
    State(config): State<Arc<Config>>,
    ApiJson(request): ApiJson<ForwardRequest>,
) -> Result<Json<ForwardResponse>, ApiError> {
    let name_ok = Regex::new(r"^\d{11}-(\d{2}-[A-Z0-9]{4}-\d{1,8}|R[ACR]-\d{8}-\d{1,5})$")
        .expect("valid regex")
        .is_match(&request.name);
    if !name_ok {
        return Err(ApiError::new(
            ErrorKind::InvalidRequest,
            "name must be RUC-TT-SERIE-NUMERO",
        ));
    }
    let url = match (request.environment, &request.url) {
        (Environment::Fake, _) => format!(
            "{}/ol-ti-itcpgem-ose/billService",
            config.sunat_fake_url.trim_end_matches('/')
        ),
        (_, Some(url)) if url.starts_with("https://") => url.clone(),
        _ => {
            return Err(ApiError::new(
                ErrorKind::InvalidRequest,
                "an https url of SUNAT's OSE service is required",
            ))
        }
    };
    let zip = zip_pair(&request.name, &request.xml, &request.cdr_xml)
        .map_err(|e| ApiError::new(ErrorKind::Other, e))?;
    let (operation, action) = match request.operation {
        Operation::SendBill => ("sendBill", "urn:sendBill"),
        Operation::SendSummary => ("sendSummary", "urn:sendSummary"),
    };
    let envelope = envelope(
        operation,
        &request.credentials,
        &format!("{}.zip", request.name),
        &STANDARD.encode(zip),
    );
    let http = reqwest::Client::builder()
        .timeout(TIMEOUT)
        // Never replay the credentials to another location
        .redirect(reqwest::redirect::Policy::none())
        .build()
        .map_err(|e| ApiError::new(ErrorKind::Other, e))?;
    let span = tracing::info_span!("sunat.ose_forward", otel.kind = "client", name = %request.name);
    let response = match http
        .post(&url)
        .header("Content-Type", "text/xml; charset=utf-8")
        .header("SOAPAction", action)
        .body(envelope)
        .send()
        .instrument(span)
        .await
    {
        Ok(response) => response,
        Err(e) => {
            let status = if e.is_connect() {
                ForwardStatus::NotDelivered
            } else {
                ForwardStatus::Unknown
            };
            return Ok(Json(ForwardResponse {
                status,
                code: None,
                description: Some(e.to_string()),
            }));
        }
    };
    let body = response.text().await.unwrap_or_default();
    Ok(Json(answer(&body)))
}

/// Reads SUNAT's answer: an acknowledgement or a fault
fn answer(body: &str) -> ForwardResponse {
    let capture = |pattern: &str| {
        Regex::new(pattern)
            .ok()
            .and_then(|re| re.captures(body))
            .and_then(|c| c.get(1))
            .map(|m| m.as_str().trim().to_string())
    };
    if let Some(fault) = capture(r"(?s)<faultcode[^>]*>([^<]*)</faultcode>") {
        let code = fault.rsplit('.').next().unwrap_or(&fault).to_string();
        let description = capture(r"(?s)<faultstring[^>]*>([^<]*)</faultstring>");
        let number = code.parse::<u32>().ok();
        let status = match (number, number.and_then(codes::classify)) {
            (Some(n), _) if (2000..4000).contains(&n) => ForwardStatus::Rejected,
            (
                _,
                Some(
                    CodeClass::TransientSystem
                    | CodeClass::TransientConstancia
                    | CodeClass::TransientInProcess,
                ),
            ) => ForwardStatus::Unknown,
            (None, _) => ForwardStatus::Unknown,
            // Credentials, package, route, 1032/1033 and anything unclassified: not the document's fault
            _ => ForwardStatus::Blocked,
        };
        return ForwardResponse {
            status,
            code: Some(code),
            description,
        };
    }
    // sendSummary: the ticket is SUNAT's acknowledgement of receipt
    if let Some(ticket) = capture(r"(?s)<ticket[^>]*>([^<]+)</ticket>") {
        return ForwardResponse {
            status: ForwardStatus::Accepted,
            code: Some(ticket),
            description: Some("ticket".into()),
        };
    }
    let Some(ack) = capture(r"(?s)<applicationResponse[^>]*>([^<]+)</applicationResponse>") else {
        return ForwardResponse {
            status: ForwardStatus::Unknown,
            code: None,
            description: Some("SUNAT's answer has neither an acknowledgement nor a fault".into()),
        };
    };
    let text = STANDARD
        .decode(ack.trim())
        .ok()
        .and_then(|bytes| unzip_first(&bytes))
        .unwrap_or_default();
    let code = Regex::new(r"<cbc:ResponseCode[^>]*>([^<]+)</cbc:ResponseCode>")
        .ok()
        .and_then(|re| re.captures(&text))
        .and_then(|c| c.get(1))
        .map(|m| m.as_str().trim().to_string());
    let description = Regex::new(r"<cbc:Description[^>]*>(?:<!\[CDATA\[)?([^<\]]+)")
        .ok()
        .and_then(|re| re.captures(&text))
        .and_then(|c| c.get(1))
        .map(|m| m.as_str().trim().to_string());
    let status = match code.as_deref() {
        Some("0") => ForwardStatus::Accepted,
        Some(c) if c.parse::<u32>().is_ok_and(|n| n >= 4000) => ForwardStatus::Accepted,
        Some(c) if c.parse::<u32>().is_ok_and(|n| (2000..4000).contains(&n)) => {
            ForwardStatus::Rejected
        }
        Some(_) => ForwardStatus::Blocked,
        None => ForwardStatus::Unknown,
    };
    ForwardResponse {
        status,
        code,
        description,
    }
}

fn zip_pair(name: &str, xml: &str, cdr_xml: &str) -> zip::result::ZipResult<Vec<u8>> {
    let mut zip = zip::ZipWriter::new(Cursor::new(Vec::new()));
    let options = zip::write::SimpleFileOptions::default();
    zip.start_file(format!("{name}.xml"), options)?;
    // The bytes the issuer signed, in the encoding the XML declares (e.g. ISO-8859-1)
    zip.write_all(xhandler::prelude::UblFile::from(xml).bytes())?;
    zip.start_file(format!("R-{name}.xml"), options)?;
    zip.write_all(cdr_xml.as_bytes())?;
    Ok(zip.finish()?.into_inner())
}

fn unzip_first(bytes: &[u8]) -> Option<String> {
    let mut archive = zip::ZipArchive::new(Cursor::new(bytes)).ok()?;
    for i in 0..archive.len() {
        let mut file = archive.by_index(i).ok()?;
        if file
            .name()
            .is_ok_and(|name| name.to_lowercase().ends_with(".xml"))
        {
            let mut text = String::new();
            file.by_ref()
                .take(16 * 1024 * 1024)
                .read_to_string(&mut text)
                .ok()?;
            return Some(text);
        }
    }
    None
}

fn envelope(
    operation: &str,
    credentials: &OseCredentials,
    file_name: &str,
    content: &str,
) -> String {
    format!(
        r#"<?xml version="1.0" encoding="UTF-8"?><soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ser="http://service.sunat.gob.pe" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"><soapenv:Header><wsse:Security><wsse:UsernameToken><wsse:Username>{user}</wsse:Username><wsse:Password>{password}</wsse:Password></wsse:UsernameToken></wsse:Security></soapenv:Header><soapenv:Body><ser:{operation}><fileName>{file}</fileName><contentFile>{content}</contentFile></ser:{operation}></soapenv:Body></soapenv:Envelope>"#,
        user = escape(&credentials.username),
        password = escape(&credentials.password),
        file = escape(file_name),
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    fn ack(code: &str) -> String {
        let mut zip = zip::ZipWriter::new(Cursor::new(Vec::new()));
        zip.start_file("R-x.xml", zip::write::SimpleFileOptions::default())
            .unwrap();
        zip.write_all(
            format!("<ar:ApplicationResponse><cbc:ResponseCode>{code}</cbc:ResponseCode><cbc:Description>ok</cbc:Description></ar:ApplicationResponse>")
                .as_bytes(),
        )
        .unwrap();
        let bytes = zip.finish().unwrap().into_inner();
        format!(
            "<soap:Envelope><soap:Body><br:sendBillResponse><applicationResponse>{}</applicationResponse></br:sendBillResponse></soap:Body></soap:Envelope>",
            STANDARD.encode(bytes)
        )
    }

    #[test]
    fn answers_are_mapped() {
        assert_eq!(answer(&ack("0")).status, ForwardStatus::Accepted);
        assert_eq!(answer(&ack("2800")).status, ForwardStatus::Rejected);
        let fault = |code: &str| {
            format!("<soap:Fault><faultcode>soap-env:Client.{code}</faultcode><faultstring>x</faultstring></soap:Fault>")
        };
        assert_eq!(answer(&fault("0109")).status, ForwardStatus::Unknown);
        assert_eq!(answer(&fault("2335")).status, ForwardStatus::Rejected);
        // Not the document's fault: kept queued for a person
        for code in ["0102", "0111", "0151", "1032", "1033", "0999"] {
            assert_eq!(
                answer(&fault(code)).status,
                ForwardStatus::Blocked,
                "{code}"
            );
        }
        assert_eq!(answer("<html>503</html>").status, ForwardStatus::Unknown);
    }

    #[test]
    fn the_zip_carries_document_and_cdr() {
        let zip = zip_pair(
            "20123456789-01-F001-1",
            "<Invoice/>",
            "<ApplicationResponse/>",
        )
        .unwrap();
        let archive = zip::ZipArchive::new(Cursor::new(zip)).unwrap();
        let names: Vec<String> = archive
            .file_names()
            .map(|name| name.unwrap().into_owned())
            .collect();
        assert!(names.contains(&"20123456789-01-F001-1.xml".to_string()));
        assert!(names.contains(&"R-20123456789-01-F001-1.xml".to_string()));
    }
}
