//! A SUNAT fake for tests and chaos runs (DESIGN.md §15.2, §24.3): speaks
//! enough of SUNAT's SOAP services and the GRE REST API for xsender, and
//! answers with real CDR zips.
//!
//! Run it with `cargo run --bin sunat-fake -- --addr 127.0.0.1:8099`, or
//! [`spawn`] it inside a test. The engine reaches it with `environment:
//! "fake"` (`XHANDLER_SUNAT_FAKE_URL`) or through `urls`.
//!
//! **Scenarios** are chosen by a marker anywhere in the document's XML text
//! (e.g. an XML comment, which doesn't change the signature):
//! `xhandler-fake:<scenario>` with scenario `accept` (default), `reject`
//! (2335), `observe` (4252 + note), `exception` (fault 0109), `collision`
//! (fault 1033), `timeout` (answers after `--timeout-secs`), `drop` (records
//! the document, then breaks the connection instead of answering),
//! `in_progress` (the summary's ticket stays at 98), `credentials` (fault
//! 0102).
//!
//! **Registry.** Received documents are kept in memory, by
//! RUC-tipo-serie-número with their SHA-256: sending the same bytes again
//! returns the first answer (like SUNAT), other bytes for the same number get
//! 1033, and the consult services answer from it. This state is fine in a
//! test tool; the engine itself keeps none.
//!
//! **Admin**: `POST /fake/mode {mode: normal|outage|slow, delay_ms?}`
//! (outage answers HTTP 503 with an HTML page, like SUNAT's proxy),
//! `GET /fake/received`, `POST /fake/reset`.

use std::collections::{BTreeMap, HashMap};
use std::io::{Cursor, Read, Write};
use std::sync::{Arc, Mutex, MutexGuard};
use std::time::Duration;

use axum::body::{Body, Bytes};
use axum::extract::{Form, Path, State};
use axum::http::{header, HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::routing::{get, post};
use axum::{Json, Router};
use base64::engine::general_purpose::STANDARD as BASE64;
use base64::Engine as _;
use chrono::Utc;
use quick_xml::events::Event;
use quick_xml::Reader;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use xhandler::prelude::UblFile;

use crate::cdr::escape;

#[path = "sunat_fake_ose.rs"]
mod ose;
#[path = "sunat_fake_sire.rs"]
mod sire;

/// RUC SUNAT signs its CDRs with
const SUNAT_RUC: &str = "20131312955";
const MARKER: &str = "xhandler-fake:";

#[derive(Clone, Debug)]
pub struct Options {
    /// How long the `timeout` scenario waits before answering
    pub timeout: Duration,
}

impl Default for Options {
    fn default() -> Self {
        Self {
            timeout: Duration::from_secs(130),
        }
    }
}

/// Starts the fake on a random local port, in the current tokio runtime.
/// Returns its base URL.
pub async fn spawn(options: Options) -> std::io::Result<String> {
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await?;
    let addr = listener.local_addr()?;
    tokio::spawn(async move {
        let _ = axum::serve(listener, router(options)).await;
    });
    Ok(format!("http://{addr}"))
}

pub fn router(options: Options) -> Router {
    let fake = Fake {
        options: Arc::new(options),
        inner: Arc::new(Mutex::new(Inner::default())),
    };
    Router::new()
        .route("/fake/mode", post(set_mode))
        .route("/fake/received", get(received))
        .route("/fake/reset", post(reset))
        .route("/v1/clientessol/{client_id}/oauth2/token/", post(gre_token))
        .route("/v1/contribuyente/gem/comprobantes/{name}", post(gre_send))
        .route(
            "/v1/contribuyente/gem/comprobantes/envios/{ticket}",
            get(gre_ticket),
        )
        .route("/fake/sire", get(sire::submitted))
        .route("/fake/ose-forwards", get(ose::forwards))
        .route("/ol-ti-itcpgem-ose/billService", post(ose::bill_service))
        .route(
            "/v1/contribuyente/migeigv/libros/rvie/propuesta/web/propuesta/{period}/exportapropuesta",
            get(sire::export_rvie),
        )
        .route(
            "/v1/contribuyente/migeigv/libros/rce/propuesta/web/propuesta/{period}/exportacioncomprobantepropuesta",
            get(sire::export_rce),
        )
        .route(
            "/v1/contribuyente/migeigv/libros/rvierce/gestionprocesosmasivos/web/masivo/consultaestadotickets",
            get(sire::ticket_state),
        )
        .route(
            "/v1/contribuyente/migeigv/libros/rvierce/gestionprocesosmasivos/web/masivo/archivoreporte",
            get(sire::report_file),
        )
        .route(
            "/v1/contribuyente/migeigv/libros/{book}/propuesta/web/propuesta/{period}/reemplazarpropuesta",
            post(sire::replace),
        )
        .route(
            "/v1/contribuyente/facturanegociable/comprobantes/{key}/conformidad",
            get(sire::conformity),
        )
        // Every other POST is SOAP: the operation is read from the body
        .fallback(soap)
        .with_state(fake)
}

#[derive(Clone)]
struct Fake {
    options: Arc<Options>,
    inner: Arc<Mutex<Inner>>,
}

#[derive(Default)]
struct Inner {
    mode: Mode,
    delay: Duration,
    received: BTreeMap<String, Received>,
    tickets: HashMap<String, TicketEntry>,
    next_ticket: u64,
    /// Bearer token → SOL username (SIRE answers per RUC)
    tokens: HashMap<String, String>,
    sire: sire::SireState,
    ose: ose::OseState,
}

#[derive(Default, Clone, Copy, PartialEq, Deserialize, Serialize, Debug)]
#[serde(rename_all = "snake_case")]
enum Mode {
    #[default]
    Normal,
    Outage,
    Slow,
}

#[derive(Clone, Serialize)]
struct Received {
    key: String,
    filename: String,
    sha256: String,
    response_code: String,
    accepted: bool,
    received_at: String,
    #[serde(skip)]
    cdr_zip: String,
    /// Facts the SIRE proposal and the conformity consult answer with
    #[serde(flatten)]
    facts: Box<sire::Facts>,
}

struct TicketEntry {
    key: String,
    in_progress: bool,
}

impl Fake {
    fn inner(&self) -> MutexGuard<'_, Inner> {
        self.inner
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
    }

    /// Outage: SUNAT's proxy page. Slow: wait first.
    async fn gate(&self) -> Option<Response> {
        let (mode, delay) = {
            let inner = self.inner();
            (inner.mode, inner.delay)
        };
        match mode {
            Mode::Normal => None,
            Mode::Slow => {
                tokio::time::sleep(delay).await;
                None
            }
            Mode::Outage => Some(
                (
                    StatusCode::SERVICE_UNAVAILABLE,
                    [(header::CONTENT_TYPE, "text/html")],
                    "<html><head><title>503 Service Unavailable</title></head><body><h1>Service Unavailable</h1></body></html>",
                )
                    .into_response(),
            ),
        }
    }
}

// --- admin ------------------------------------------------------------------

#[derive(Deserialize)]
struct ModeRequest {
    mode: Mode,
    delay_ms: Option<u64>,
}

async fn set_mode(State(fake): State<Fake>, Json(request): Json<ModeRequest>) -> StatusCode {
    let mut inner = fake.inner();
    inner.mode = request.mode;
    inner.delay = Duration::from_millis(request.delay_ms.unwrap_or(5_000));
    tracing::info!(mode = ?request.mode, "fake mode");
    StatusCode::NO_CONTENT
}

async fn received(State(fake): State<Fake>) -> Json<Vec<Received>> {
    Json(fake.inner().received.values().cloned().collect())
}

async fn reset(State(fake): State<Fake>) -> StatusCode {
    *fake.inner() = Inner::default();
    StatusCode::NO_CONTENT
}

// --- documents --------------------------------------------------------------

#[derive(Clone, Copy, PartialEq, Debug)]
enum Scenario {
    Accept,
    Reject,
    Observe,
    Exception,
    Collision,
    Timeout,
    Drop,
    InProgress,
    Credentials,
}

fn scenario(text: &str) -> Scenario {
    let Some(start) = text.find(MARKER) else {
        return Scenario::Accept;
    };
    let name: String = text[start + MARKER.len()..]
        .chars()
        .take_while(|c| c.is_ascii_lowercase() || *c == '_')
        .collect();
    match name.as_str() {
        "reject" => Scenario::Reject,
        "observe" => Scenario::Observe,
        "exception" => Scenario::Exception,
        "collision" => Scenario::Collision,
        "timeout" => Scenario::Timeout,
        "drop" => Scenario::Drop,
        "in_progress" => Scenario::InProgress,
        "credentials" => Scenario::Credentials,
        _ => Scenario::Accept,
    }
}

/// A document received in a zip
struct Doc {
    /// File name without extension: RUC-TT-SERIE-NUMERO
    name: String,
    text: String,
    sha256: String,
    key: String,
    document_id: String,
    ruc: String,
}

/// Fault answer: (SUNAT code, message)
type Fault = (&'static str, String);

fn unzip(zip_base64: &str) -> Result<Vec<u8>, Fault> {
    let corrupt = |_| ("0156", "El archivo ZIP esta corrupto".to_string());
    let bytes = BASE64
        .decode(zip_base64.trim())
        .map_err(|_| ("0156", "El archivo ZIP esta corrupto".to_string()))?;
    let mut archive = zip::ZipArchive::new(Cursor::new(bytes)).map_err(corrupt)?;
    let name = archive
        .file_names()
        .filter_map(Result::ok)
        .find(|name| name.to_lowercase().ends_with(".xml"))
        .map(String::from)
        .ok_or((
            "0157",
            "El archivo ZIP no contiene comprobantes".to_string(),
        ))?;
    let mut xml = Vec::new();
    archive
        .by_name(&name)
        .map_err(corrupt)?
        .read_to_end(&mut xml)
        .map_err(|_| ("0156", "El archivo ZIP esta corrupto".to_string()))?;
    Ok(xml)
}

fn read_doc(filename: &str, zip_base64: &str) -> Result<Doc, Fault> {
    let xml = unzip(zip_base64)?;
    let sha256 = hex(&Sha256::digest(&xml));
    let file = UblFile::from_bytes(xml).map_err(|_| {
        (
            "0306",
            "No se puede leer (parsear) el archivo XML".to_string(),
        )
    })?;
    let metadata = file.metadata().map_err(|_| {
        (
            "0306",
            "No se puede leer (parsear) el archivo XML".to_string(),
        )
    })?;
    let name = filename
        .trim_end_matches(".zip")
        .trim_end_matches(".ZIP")
        .to_string();
    let key = key_of(&name).ok_or((
        "0151",
        "El nombre del archivo ZIP es incorrecto".to_string(),
    ))?;
    Ok(Doc {
        name,
        text: file.content().to_string(),
        sha256,
        key,
        document_id: metadata.document_id,
        ruc: metadata.ruc,
    })
}

/// RUC-TT-SERIE-NUMERO with the number without leading zeros
fn key_of(name: &str) -> Option<String> {
    let parts: Vec<&str> = name.split('-').collect();
    let [ruc, tipo, serie, numero] = parts.as_slice() else {
        return None;
    };
    Some(document_key(ruc, tipo, serie, numero))
}

fn document_key(ruc: &str, tipo: &str, serie: &str, numero: &str) -> String {
    let numero = numero
        .trim()
        .parse::<u64>()
        .map(|n| n.to_string())
        .unwrap_or_else(|_| numero.trim().to_string());
    format!(
        "{}-{}-{}-{numero}",
        ruc.trim(),
        tipo.trim(),
        serie.trim().to_uppercase()
    )
}

fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

enum Delivery {
    Now,
    Late,
    Drop,
}

enum Processed {
    Fault(Fault),
    Stored(Received, Delivery),
}

/// Applies the scenario and the registry to a received document
fn process(fake: &Fake, doc: &Doc) -> Processed {
    let scenario = scenario(&doc.text);
    match scenario {
        Scenario::Credentials => return Processed::Fault(bad_credentials()),
        Scenario::Exception => {
            return Processed::Fault((
                "0109",
                "El sistema no puede responder su solicitud. (El servicio de autenticación no está disponible)".into(),
            ))
        }
        Scenario::Collision => return Processed::Fault(collision()),
        _ => {}
    }
    let mut inner = fake.inner();
    if let Some(previous) = inner.received.get(&doc.key) {
        // Like SUNAT: the same bytes get the first answer again
        return if previous.sha256 == doc.sha256 {
            Processed::Stored(previous.clone(), Delivery::Now)
        } else {
            Processed::Fault(collision())
        };
    }
    let (code, description, notes, accepted): (&str, String, Vec<String>, bool) = match scenario {
        Scenario::Reject => (
            "2335",
            "El documento electrónico ingresado ha sido alterado".into(),
            vec![],
            false,
        ),
        Scenario::Observe => (
            "4252",
            format!(
                "El comprobante {}, ha sido aceptado con observaciones",
                doc.document_id
            ),
            vec!["4252 - El dato ingresado como atributo @listName es incorrecto.".into()],
            true,
        ),
        _ => (
            "0",
            format!("El comprobante {}, ha sido aceptado", doc.document_id),
            vec![],
            true,
        ),
    };
    let received = Received {
        key: doc.key.clone(),
        filename: format!("{}.zip", doc.name),
        sha256: doc.sha256.clone(),
        response_code: code.into(),
        accepted,
        received_at: Utc::now().to_rfc3339(),
        cdr_zip: cdr_zip(
            &doc.name,
            &doc.document_id,
            &doc.ruc,
            code,
            &description,
            &notes,
        ),
        facts: Box::new(sire::Facts::of(&doc.text, accepted)),
    };
    inner.received.insert(doc.key.clone(), received.clone());
    tracing::info!(key = %doc.key, ?scenario, code, "fake received a document");
    let delivery = match scenario {
        Scenario::Timeout => Delivery::Late,
        Scenario::Drop => Delivery::Drop,
        _ => Delivery::Now,
    };
    Processed::Stored(received, delivery)
}

fn bad_credentials() -> Fault {
    ("0102", "Usuario o contraseña incorrectos".into())
}

fn collision() -> Fault {
    (
        "1033",
        "El comprobante fue registrado previamente con otros datos".into(),
    )
}

/// A SUNAT-style CDR (ApplicationResponse) zipped as R-<file>.zip
fn cdr_zip(
    name: &str,
    document_id: &str,
    ruc: &str,
    code: &str,
    description: &str,
    notes: &[String],
) -> String {
    let now = Utc::now();
    let notes: String = notes
        .iter()
        .map(|note| format!("<cbc:Note>{}</cbc:Note>", escape(note)))
        .collect();
    let xml = format!(
        concat!(
            r#"<?xml version="1.0" encoding="UTF-8"?>"#,
            r#"<ar:ApplicationResponse xmlns:ar="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2" xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">"#,
            "<cbc:UBLVersionID>2.0</cbc:UBLVersionID><cbc:CustomizationID>1.0</cbc:CustomizationID>",
            "<cbc:ID>{id}</cbc:ID><cbc:IssueDate>{date}</cbc:IssueDate><cbc:IssueTime>{time}</cbc:IssueTime>",
            "<cbc:ResponseDate>{date}</cbc:ResponseDate><cbc:ResponseTime>{time}</cbc:ResponseTime>{notes}",
            "<cac:SenderParty><cac:PartyIdentification><cbc:ID>{sunat}</cbc:ID></cac:PartyIdentification></cac:SenderParty>",
            "<cac:ReceiverParty><cac:PartyIdentification><cbc:ID>{ruc}</cbc:ID></cac:PartyIdentification></cac:ReceiverParty>",
            "<cac:DocumentResponse><cac:Response><cbc:ReferenceID>{document}</cbc:ReferenceID>",
            "<cbc:ResponseCode>{code}</cbc:ResponseCode><cbc:Description>{description}</cbc:Description></cac:Response>",
            "<cac:DocumentReference><cbc:ID>{document}</cbc:ID></cac:DocumentReference></cac:DocumentResponse>",
            "</ar:ApplicationResponse>"
        ),
        id = now.timestamp_millis(),
        date = now.format("%Y-%m-%d"),
        time = now.format("%H:%M:%S"),
        notes = notes,
        sunat = SUNAT_RUC,
        ruc = escape(ruc),
        document = escape(document_id),
        code = code,
        description = escape(description),
    );
    let mut zip = zip::ZipWriter::new(Cursor::new(Vec::new()));
    let options = zip::write::SimpleFileOptions::default();
    let written = (|| -> zip::result::ZipResult<Vec<u8>> {
        zip.add_directory("dummy/", options)?;
        zip.start_file(format!("R-{name}.xml"), options)?;
        zip.write_all(xml.as_bytes())?;
        Ok(zip.finish()?.into_inner())
    })();
    BASE64.encode(written.expect("zips in memory"))
}

// --- SOAP -------------------------------------------------------------------

struct SoapRequest {
    operation: String,
    /// Text of the leaf elements by local name (first occurrence)
    fields: HashMap<String, String>,
}

impl SoapRequest {
    fn field(&self, name: &str) -> &str {
        self.fields
            .get(name)
            .map(String::as_str)
            .unwrap_or_default()
    }
}

fn parse_soap(body: &str) -> Option<SoapRequest> {
    let mut reader = Reader::from_str(body);
    let mut path: Vec<String> = Vec::new();
    let mut operation = None;
    let mut fields = HashMap::new();
    let mut text = String::new();
    loop {
        match reader.read_event().ok()? {
            Event::DocType(_) => return None,
            Event::Start(start) => {
                let name = String::from_utf8_lossy(start.local_name().as_ref()).into_owned();
                if operation.is_none() && path.last().is_some_and(|p| p == "Body") {
                    operation = Some(name.clone());
                }
                path.push(name);
                text.clear();
            }
            Event::Text(t) => text.push_str(&t.xml10_content().ok()?),
            Event::GeneralRef(r) => crate::inspect::resolve_reference(&r, &mut text).ok()?,
            Event::End(_) => {
                if let Some(name) = path.pop() {
                    let value = text.trim();
                    if !value.is_empty() {
                        fields.entry(name).or_insert_with(|| value.to_string());
                    }
                }
                text.clear();
            }
            Event::Eof => break,
            _ => {}
        }
    }
    Some(SoapRequest {
        operation: operation?,
        fields,
    })
}

/// SUNAT's beta accepts any RUC + MODDATOS with password MODDATOS; the fake
/// also accepts any other non-empty user and password
fn authenticated(username: &str, password: &str) -> bool {
    if username.to_uppercase().ends_with("MODDATOS") {
        password == "MODDATOS"
    } else {
        !username.is_empty() && !password.is_empty()
    }
}

const ENVELOPE_OPEN: &str = r#"<?xml version="1.0" encoding="UTF-8"?><soap-env:Envelope xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/"><soap-env:Header/><soap-env:Body>"#;
const ENVELOPE_CLOSE: &str = "</soap-env:Body></soap-env:Envelope>";

fn soap_ok(operation: &str, content: String) -> Response {
    let body = format!(
        r#"{ENVELOPE_OPEN}<br:{operation}Response xmlns:br="http://service.sunat.gob.pe">{content}</br:{operation}Response>{ENVELOPE_CLOSE}"#
    );
    (
        StatusCode::OK,
        [(header::CONTENT_TYPE, "text/xml;charset=UTF-8")],
        body,
    )
        .into_response()
}

fn soap_fault((code, message): Fault) -> Response {
    let body = format!(
        "{ENVELOPE_OPEN}<soap-env:Fault><faultcode>soap-env:Client.{code}</faultcode><faultstring>{}</faultstring></soap-env:Fault>{ENVELOPE_CLOSE}",
        escape(&message)
    );
    (
        StatusCode::INTERNAL_SERVER_ERROR,
        [(header::CONTENT_TYPE, "text/xml;charset=UTF-8")],
        body,
    )
        .into_response()
}

fn status(code: &str, message: Option<&str>, content: Option<&str>) -> String {
    let mut xml = String::from("<status>");
    if let Some(content) = content {
        xml.push_str(&format!("<content>{content}</content>"));
    }
    xml.push_str(&format!("<statusCode>{code}</statusCode>"));
    if let Some(message) = message {
        xml.push_str(&format!(
            "<statusMessage>{}</statusMessage>",
            escape(message)
        ));
    }
    xml.push_str("</status>");
    xml
}

/// A 200 whose body fails: the client sees the connection break after
/// sending, without an answer
fn dropped() -> Response {
    let stream = futures_util::stream::once(async {
        Err::<Bytes, std::io::Error>(std::io::Error::other("connection dropped by the fake"))
    });
    Response::builder()
        .status(StatusCode::OK)
        .header(header::CONTENT_TYPE, "text/xml;charset=UTF-8")
        .body(Body::from_stream(stream))
        .expect("valid response")
}

async fn deliver(fake: &Fake, delivery: Delivery, answer: Response) -> Response {
    match delivery {
        Delivery::Now => answer,
        Delivery::Late => {
            tokio::time::sleep(fake.options.timeout).await;
            answer
        }
        Delivery::Drop => dropped(),
    }
}

async fn soap(State(fake): State<Fake>, body: String) -> Response {
    if let Some(response) = fake.gate().await {
        return response;
    }
    let Some(request) = parse_soap(&body) else {
        return soap_fault(("0306", "No se puede leer (parsear) el mensaje SOAP".into()));
    };
    if !authenticated(request.field("Username"), request.field("Password")) {
        return soap_fault(bad_credentials());
    }
    match request.operation.as_str() {
        "sendBill" => send_bill(&fake, &request).await,
        "sendSummary" => send_summary(&fake, &request).await,
        "getStatus" if request.fields.contains_key("ticket") => ticket_status(&fake, &request),
        "getStatus" | "getStatusCdr" => consult(&fake, &request),
        "validaCDPcriterios" => valida_cdp(&fake, &request),
        "verificaCPEarchivo" => verifica_archivo(&fake, &request),
        operation => soap_fault((
            "0100",
            format!("Operación no soportada por el fake: {operation}"),
        )),
    }
}

async fn send_bill(fake: &Fake, request: &SoapRequest) -> Response {
    let doc = match read_doc(request.field("fileName"), request.field("contentFile")) {
        Ok(doc) => doc,
        Err(fault) => return soap_fault(fault),
    };
    match process(fake, &doc) {
        Processed::Fault(fault) => soap_fault(fault),
        Processed::Stored(received, delivery) => {
            let answer = soap_ok(
                "sendBill",
                format!(
                    "<applicationResponse>{}</applicationResponse>",
                    received.cdr_zip
                ),
            );
            deliver(fake, delivery, answer).await
        }
    }
}

fn new_ticket(fake: &Fake, prefix: &str, key: &str, in_progress: bool) -> String {
    let mut inner = fake.inner();
    inner.next_ticket += 1;
    let ticket = format!("{prefix}{}", 1_700_000_000_000u64 + inner.next_ticket);
    inner.tickets.insert(
        ticket.clone(),
        TicketEntry {
            key: key.to_string(),
            in_progress,
        },
    );
    ticket
}

async fn send_summary(fake: &Fake, request: &SoapRequest) -> Response {
    let doc = match read_doc(request.field("fileName"), request.field("contentFile")) {
        Ok(doc) => doc,
        Err(fault) => return soap_fault(fault),
    };
    match process(fake, &doc) {
        Processed::Fault(fault) => soap_fault(fault),
        Processed::Stored(received, delivery) => {
            let in_progress = scenario(&doc.text) == Scenario::InProgress;
            let ticket = new_ticket(fake, "", &received.key, in_progress);
            let answer = soap_ok("sendSummary", format!("<ticket>{ticket}</ticket>"));
            deliver(fake, delivery, answer).await
        }
    }
}

/// The outcome behind a ticket: Ok(None) while in progress
fn ticket_outcome(fake: &Fake, ticket: &str) -> Result<Option<Received>, ()> {
    let inner = fake.inner();
    let entry = inner.tickets.get(ticket).ok_or(())?;
    if entry.in_progress {
        return Ok(None);
    }
    inner.received.get(&entry.key).cloned().map(Some).ok_or(())
}

fn ticket_status(fake: &Fake, request: &SoapRequest) -> Response {
    match ticket_outcome(fake, request.field("ticket")) {
        Err(()) => soap_fault(("0127", "El ticket no existe".into())),
        Ok(None) => soap_ok("getStatus", status("98", None, None)),
        Ok(Some(received)) => {
            let code = if received.accepted { "0" } else { "99" };
            soap_ok("getStatus", status(code, None, Some(&received.cdr_zip)))
        }
    }
}

fn consult(fake: &Fake, request: &SoapRequest) -> Response {
    let key = document_key(
        request.field("rucComprobante"),
        request.field("tipoComprobante"),
        request.field("serieComprobante"),
        request.field("numeroComprobante"),
    );
    let found = fake.inner().received.get(&key).cloned();
    let with_cdr = request.operation == "getStatusCdr";
    let content = match (&found, with_cdr) {
        (None, _) => status(
            "0011",
            Some("El comprobante de pago electrónico no existe"),
            None,
        ),
        (Some(received), true) => status(
            "0004",
            Some("La constancia existe"),
            Some(&received.cdr_zip),
        ),
        (Some(received), false) if received.accepted => {
            status("0001", Some("El comprobante existe y está aceptado"), None)
        }
        (Some(_), false) => status(
            "0002",
            Some("El comprobante existe pero está rechazado"),
            None,
        ),
    };
    let content = if with_cdr {
        content
            .replace("<status>", "<statusCdr>")
            .replace("</status>", "</statusCdr>")
    } else {
        content
    };
    soap_ok(&request.operation, content)
}

fn valida_cdp(fake: &Fake, request: &SoapRequest) -> Response {
    let key = document_key(
        request.field("rucEmisor"),
        request.field("tipoCDP"),
        request.field("serieCDP"),
        request.field("numeroCDP"),
    );
    let message = match fake.inner().received.get(&key) {
        None => "El comprobante no existe.",
        Some(received) if received.accepted => "El comprobante existe y está aceptado.",
        Some(_) => "El comprobante existe pero está rechazado.",
    };
    soap_ok("validaCDPcriterios", status("0", Some(message), None))
}

fn verifica_archivo(fake: &Fake, request: &SoapRequest) -> Response {
    let doc = match read_doc(request.field("nombre"), request.field("archivo")) {
        Ok(doc) => doc,
        Err(fault) => return soap_fault(fault),
    };
    let (code, message) = match fake.inner().received.get(&doc.key) {
        None => ("2", "El comprobante no existe en SUNAT."),
        Some(received) if received.sha256 == doc.sha256 => (
            "0",
            "El comprobante es válido y corresponde al registrado en SUNAT.",
        ),
        Some(_) => ("1", "El comprobante no corresponde al registrado en SUNAT."),
    };
    soap_ok("verificaCPEarchivo", status(code, Some(message), None))
}

// --- GRE REST ---------------------------------------------------------------

#[derive(Deserialize)]
struct TokenForm {
    client_secret: Option<String>,
    username: Option<String>,
    password: Option<String>,
}

async fn gre_token(
    State(fake): State<Fake>,
    Path(client_id): Path<String>,
    Form(form): Form<TokenForm>,
) -> Response {
    if let Some(response) = fake.gate().await {
        return response;
    }
    let username = form.username.unwrap_or_default();
    let password = form.password.unwrap_or_default();
    if client_id.is_empty()
        || form.client_secret.unwrap_or_default().is_empty()
        || !authenticated(&username, &password)
    {
        return (
            StatusCode::BAD_REQUEST,
            Json(serde_json::json!({ "error": "invalid_client", "error_description": "cliente o credenciales inválidos" })),
        )
            .into_response();
    }
    let token = format!(
        "fake-{}",
        hex(&Sha256::digest(
            format!("{client_id}:{username}").as_bytes()
        ))[..16]
            .to_owned()
    );
    fake.inner().tokens.insert(token.clone(), username.clone());
    Json(serde_json::json!({ "access_token": token, "token_type": "JWT", "expires_in": 3600 }))
        .into_response()
}

fn authorized(headers: &HeaderMap) -> bool {
    headers
        .get(header::AUTHORIZATION)
        .and_then(|value| value.to_str().ok())
        .is_some_and(|value| value.starts_with("Bearer fake-"))
}

fn rest_error(status: StatusCode, (code, message): Fault) -> Response {
    (
        status,
        Json(serde_json::json!({
            "cod": status.as_u16().to_string(),
            "msg": status.canonical_reason().unwrap_or_default(),
            "errors": [{ "cod": code, "msg": message }],
        })),
    )
        .into_response()
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Archivo {
    nom_archivo: String,
    arc_gre_zip: String,
    hash_zip: String,
}

#[derive(Deserialize)]
struct GreSend {
    archivo: Archivo,
}

async fn gre_send(
    State(fake): State<Fake>,
    headers: HeaderMap,
    Json(request): Json<GreSend>,
) -> Response {
    if let Some(response) = fake.gate().await {
        return response;
    }
    if !authorized(&headers) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({ "cod": "401", "msg": "No autorizado" })),
        )
            .into_response();
    }
    let archivo = request.archivo;
    let zip_hash = BASE64
        .decode(archivo.arc_gre_zip.trim())
        .map(|zip| hex(&Sha256::digest(zip)))
        .unwrap_or_default();
    if zip_hash != archivo.hash_zip {
        return rest_error(
            StatusCode::UNPROCESSABLE_ENTITY,
            ("0156", "El hash del archivo ZIP no corresponde".into()),
        );
    }
    let doc = match read_doc(&archivo.nom_archivo, &archivo.arc_gre_zip) {
        Ok(doc) => doc,
        Err(fault) => return rest_error(StatusCode::UNPROCESSABLE_ENTITY, fault),
    };
    match process(&fake, &doc) {
        Processed::Fault(fault) => rest_error(StatusCode::UNPROCESSABLE_ENTITY, fault),
        Processed::Stored(received, delivery) => {
            let in_progress = scenario(&doc.text) == Scenario::InProgress;
            let ticket = new_ticket(&fake, "fake-gre-", &received.key, in_progress);
            let answer = Json(serde_json::json!({
                "numTicket": ticket,
                "fecRecepcion": Utc::now().format("%Y-%m-%dT%H:%M:%S").to_string(),
            }))
            .into_response();
            deliver(&fake, delivery, answer).await
        }
    }
}

async fn gre_ticket(
    State(fake): State<Fake>,
    headers: HeaderMap,
    Path(ticket): Path<String>,
) -> Response {
    if let Some(response) = fake.gate().await {
        return response;
    }
    if !authorized(&headers) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({ "cod": "401", "msg": "No autorizado" })),
        )
            .into_response();
    }
    match ticket_outcome(&fake, &ticket) {
        Err(()) => (
            StatusCode::NOT_FOUND,
            Json(serde_json::json!({ "status": 404, "message": "Ticket no encontrado" })),
        )
            .into_response(),
        Ok(None) => Json(serde_json::json!({ "codRespuesta": "98" })).into_response(),
        Ok(Some(received)) => Json(serde_json::json!({
            "codRespuesta": if received.accepted { "0" } else { "99" },
            "indCdrGenerado": "1",
            "arcCdr": received.cdr_zip,
        }))
        .into_response(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn scenarios_come_from_the_marker() {
        assert_eq!(scenario("<Invoice/>"), Scenario::Accept);
        assert_eq!(scenario("<!-- xhandler-fake:reject -->"), Scenario::Reject);
        assert_eq!(
            scenario("<x>xhandler-fake:in_progress</x>"),
            Scenario::InProgress
        );
        assert_eq!(scenario("xhandler-fake:unknown"), Scenario::Accept);
    }

    #[test]
    fn keys_ignore_leading_zeros() {
        assert_eq!(
            key_of("12345678912-01-F001-00000001").as_deref(),
            Some("12345678912-01-F001-1")
        );
        assert_eq!(
            document_key("12345678912", "01", "f001", "1"),
            "12345678912-01-F001-1"
        );
        assert_eq!(key_of("bad"), None);
    }

    #[test]
    fn cdrs_are_readable_by_xsender() {
        let zip = cdr_zip(
            "12345678912-01-F001-1",
            "F001-1",
            "12345678912",
            "4252",
            "ok",
            &["4252 - nota".into()],
        );
        let cdr = xhandler::prelude::CdrMetadata::from_zip_base64(&zip).unwrap();
        assert_eq!(cdr.response_code, "4252");
        assert_eq!(cdr.notes, ["4252 - nota"]);
    }

    #[test]
    fn parses_soap_requests() {
        let body = r#"<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Header><wsse:Security xmlns:wsse="x"><wsse:UsernameToken><wsse:Username>12345678959MODDATOS</wsse:Username><wsse:Password>MODDATOS</wsse:Password></wsse:UsernameToken></wsse:Security></soap:Header><soap:Body><ns2:getStatus xmlns:ns2="http://service.sunat.gob.pe"><ticket>123</ticket></ns2:getStatus></soap:Body></soap:Envelope>"#;
        let request = parse_soap(body).unwrap();
        assert_eq!(request.operation, "getStatus");
        assert_eq!(request.field("ticket"), "123");
        assert!(authenticated(
            request.field("Username"),
            request.field("Password")
        ));
        assert!(!authenticated("12345678959MODDATOS", "x"));
        assert!(!authenticated("", ""));
    }
}
