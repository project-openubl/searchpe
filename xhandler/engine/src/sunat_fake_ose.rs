//! The fake's side of OSE forwarding (DESIGN.md §11.4 step 6): SUNAT's OSE
//! `billService` takes a ZIP with the issuer's document and the CDR-OSE and
//! acknowledges it. Forwards are listed at `GET /fake/ose-forwards`.

use std::io::{Cursor, Read, Write};

use axum::extract::State;
use axum::response::Response;
use axum::Json;
use base64::Engine as _;
use chrono::Utc;
use serde::Serialize;

use super::{authenticated, bad_credentials, parse_soap, soap_fault, soap_ok, Fake, BASE64};

#[derive(Default)]
pub struct OseState {
    forwards: Vec<Forward>,
}

#[derive(Clone, Serialize)]
pub struct Forward {
    name: String,
    files: Vec<String>,
    received_at: String,
}

pub async fn bill_service(State(fake): State<Fake>, body: String) -> Response {
    if let Some(response) = fake.gate().await {
        return response;
    }
    let Some(request) = parse_soap(&body) else {
        return soap_fault(("0306", "No se puede leer (parsear) el mensaje SOAP".into()));
    };
    if !authenticated(request.field("Username"), request.field("Password")) {
        return soap_fault(bad_credentials());
    }
    let summary = request.operation == "sendSummary";
    if request.operation != "sendBill" && !summary {
        return soap_fault(("0100", "Operación no soportada".into()));
    }
    let name = request
        .field("fileName")
        .trim_end_matches(".zip")
        .to_string();
    let Ok(bytes) = BASE64.decode(request.field("contentFile").trim()) else {
        return soap_fault(("0156", "El archivo ZIP esta corrupto".into()));
    };
    let Ok(mut archive) = zip::ZipArchive::new(Cursor::new(bytes)) else {
        return soap_fault(("0156", "El archivo ZIP esta corrupto".into()));
    };
    let files: Vec<String> = archive.file_names().map(String::from).collect();
    let document = format!("{name}.xml");
    let cdr = format!("R-{name}.xml");
    if !files.contains(&document) || !files.contains(&cdr) {
        return soap_fault((
            "0157",
            "El archivo ZIP debe contener el comprobante y su CDR-OSE".into(),
        ));
    }
    let mut cdr_text = String::new();
    if archive
        .by_name(&cdr)
        .ok()
        .and_then(|mut f| f.read_to_string(&mut cdr_text).ok())
        .is_none()
        || !cdr_text.contains("ApplicationResponse")
    {
        return soap_fault(("0306", "No se puede leer el CDR-OSE".into()));
    }
    fake.inner().ose.forwards.push(Forward {
        name: name.clone(),
        files,
        received_at: Utc::now().to_rfc3339(),
    });
    let ack = format!(
        "<ar:ApplicationResponse xmlns:ar=\"urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2\" xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\"><cbc:ResponseCode>0</cbc:ResponseCode><cbc:Description>Recibido {name}</cbc:Description></ar:ApplicationResponse>"
    );
    let mut zip = zip::ZipWriter::new(Cursor::new(Vec::new()));
    let written = (|| -> zip::result::ZipResult<Vec<u8>> {
        zip.start_file(
            format!("R-{name}.xml"),
            zip::write::SimpleFileOptions::default(),
        )?;
        zip.write_all(ack.as_bytes())?;
        Ok(zip.finish()?.into_inner())
    })();
    if summary {
        let ticket = format!("OSE{}", Utc::now().timestamp_millis());
        return soap_ok("sendSummary", format!("<ticket>{ticket}</ticket>"));
    }
    soap_ok(
        "sendBill",
        format!(
            "<applicationResponse>{}</applicationResponse>",
            BASE64.encode(written.expect("zips in memory"))
        ),
    )
}

pub async fn forwards(State(fake): State<Fake>) -> Json<Vec<Forward>> {
    Json(fake.inner().ose.forwards.clone())
}
