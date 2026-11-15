//! HTTP API of the engine (DESIGN.md §4.1). Stateless: every request carries
//! what it needs, and nothing is stored.

use axum::extract::{DefaultBodyLimit, Json};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::routing::{get, post};
use axum::Router;
use base64::Engine as _;
use chrono::{FixedOffset, NaiveDate, Utc};
use serde::{Deserialize, Serialize};
use tracing::Instrument;
use xhandler::prelude::*;

/// SUNAT's public demo key and certificate, accepted only by SUNAT beta.
const BETA_PRIVATE_KEY: &str = include_str!("../resources/beta/private.key");
const BETA_CERTIFICATE: &str = include_str!("../resources/beta/public.cer");
/// SUNAT beta's public SOL user.
const BETA_USERNAME: &str = "12345678959MODDATOS";
const BETA_PASSWORD: &str = "MODDATOS";

/// Peru has no daylight saving time: America/Lima is always UTC-5.
const LIMA_OFFSET_SECONDS: i32 = 5 * 3600;

const MAX_BODY_BYTES: usize = 10 * 1024 * 1024;

pub fn router() -> Router {
    Router::new()
        .route("/health", get(|| async { "ok" }))
        .route("/v1/build", post(build))
        .route("/v1/sign", post(sign))
        .route("/v1/send", post(send))
        .layer(DefaultBodyLimit::max(MAX_BODY_BYTES))
}

// --- errors -----------------------------------------------------------------

#[derive(Debug, Serialize, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum ErrorKind {
    /// The document can't be built, signed or sent as it is
    InvalidDocument,
    InvalidCertificate,
    WrongPassword,
    /// The request itself is incomplete or contradictory
    InvalidRequest,
    Other,
}

#[derive(Debug, Serialize)]
pub struct ApiError {
    pub kind: ErrorKind,
    pub message: String,
}

impl ApiError {
    fn new(kind: ErrorKind, message: impl ToString) -> Self {
        Self {
            kind,
            message: message.to_string(),
        }
    }
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let status = match self.kind {
            ErrorKind::Other => StatusCode::INTERNAL_SERVER_ERROR,
            _ => StatusCode::UNPROCESSABLE_ENTITY,
        };
        if status.is_server_error() {
            tracing::error!(message = %self.message, "request failed");
        }
        (status, Json(self)).into_response()
    }
}

// --- build ------------------------------------------------------------------

#[derive(Deserialize)]
pub struct BuildRequest {
    pub document: Document,
    /// Issue date used when the document has none. Defaults to today in Lima.
    pub fecha_emision: Option<NaiveDate>,
}

#[derive(Serialize)]
pub struct BuildResponse {
    pub xml: String,
    /// The document after enrichment (totals, legends): PDFs and reports are
    /// rendered from it, so the service never computes taxes.
    pub enriched_json: serde_json::Value,
}

async fn build(Json(request): Json<BuildRequest>) -> Result<Json<BuildResponse>, ApiError> {
    let mut document = request.document;
    let fecha_emision = request.fecha_emision.unwrap_or_else(lima_today);
    document
        .enrich(&Defaults::new(fecha_emision))
        .map_err(|e| ApiError::new(ErrorKind::InvalidDocument, e))?;
    let xml = document
        .render()
        .map_err(|e| ApiError::new(ErrorKind::InvalidDocument, e))?;
    let enriched_json =
        serde_json::to_value(&document).map_err(|e| ApiError::new(ErrorKind::Other, e))?;
    Ok(Json(BuildResponse { xml, enriched_json }))
}

fn lima_today() -> NaiveDate {
    let lima = FixedOffset::west_opt(LIMA_OFFSET_SECONDS).expect("valid offset");
    Utc::now().with_timezone(&lima).date_naive()
}

// --- sign -------------------------------------------------------------------

#[derive(Deserialize)]
pub struct SignRequest {
    pub xml: String,
    /// PKCS#12 keystore to sign with. Temporary until the HSM two-step
    /// signing of R1 (DESIGN.md §19).
    pub pkcs12: Option<Pkcs12>,
    /// Sign with SUNAT's public beta demo key instead. Must be explicit, so a
    /// missing keystore is an error rather than a document SUNAT production
    /// will reject.
    #[serde(default)]
    pub demo_key: bool,
}

#[derive(Deserialize)]
pub struct Pkcs12 {
    pub base64: String,
    pub password: String,
}

#[derive(Serialize)]
pub struct SignResponse {
    pub xml: String,
    pub digest_value: Option<String>,
}

async fn sign(Json(request): Json<SignRequest>) -> Result<Json<SignResponse>, ApiError> {
    // RSA signing is CPU-bound: keep it off the async workers.
    let span = tracing::Span::current();
    tokio::task::spawn_blocking(move || span.in_scope(|| sign_blocking(request)))
        .await
        .map_err(|e| ApiError::new(ErrorKind::Other, e))?
        .map(Json)
}

fn sign_blocking(request: SignRequest) -> Result<SignResponse, ApiError> {
    let key_pair = match (&request.pkcs12, request.demo_key) {
        (Some(_), true) => {
            return Err(ApiError::new(
                ErrorKind::InvalidRequest,
                "pkcs12 and demo_key are mutually exclusive",
            ))
        }
        (Some(pkcs12), false) => {
            let bytes = base64::engine::general_purpose::STANDARD
                .decode(&pkcs12.base64)
                .map_err(|e| ApiError::new(ErrorKind::InvalidCertificate, e))?;
            RsaKeyPair::from_pkcs12(&bytes, &pkcs12.password).map_err(|e| match e {
                SignError::WrongPassword => ApiError::new(ErrorKind::WrongPassword, e),
                _ => ApiError::new(ErrorKind::InvalidCertificate, e),
            })?
        }
        (None, true) => RsaKeyPair::from_pem(BETA_PRIVATE_KEY, BETA_CERTIFICATE)
            .map_err(|e| ApiError::new(ErrorKind::Other, e))?,
        (None, false) => {
            return Err(ApiError::new(
                ErrorKind::InvalidRequest,
                "either pkcs12 or demo_key is required",
            ))
        }
    };

    let signed = XSigner {
        xml_document: request.xml,
    }
    .sign(&key_pair)
    .map_err(|e| ApiError::new(ErrorKind::InvalidDocument, e))?;
    let xml = String::from_utf8(signed).map_err(|e| ApiError::new(ErrorKind::Other, e))?;
    let digest_value = UblFile::from(xml.as_str())
        .metadata()
        .map_err(|e| ApiError::new(ErrorKind::InvalidDocument, e))?
        .digest_value;
    Ok(SignResponse { xml, digest_value })
}

/// True when the XML is signed with SUNAT's beta demo certificate.
fn signed_with_demo_key(xml: &str) -> bool {
    let certificate: String = BETA_CERTIFICATE
        .lines()
        .filter(|line| !line.starts_with("-----"))
        .collect();
    let xml: String = xml.chars().filter(|c| !c.is_whitespace()).collect();
    xml.contains(&certificate)
}

// --- send -------------------------------------------------------------------

#[derive(Deserialize, Clone, Copy, Debug, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum Environment {
    Beta,
    Production,
}

#[derive(Deserialize)]
pub struct SendRequest {
    pub xml: String,
    pub environment: Environment,
    /// SOL credentials. Required in production; beta uses SUNAT's public user.
    pub credentials: Option<SolCredentials>,
}

#[derive(Deserialize)]
pub struct SolCredentials {
    pub username: String,
    pub password: String,
}

/// What SUNAT answered. The service decides what each outcome means
/// (DESIGN.md §9.2).
#[derive(Serialize, Debug, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum SunatStatus {
    Accepted,
    Rejected,
    Exception,
    InProgress,
    /// No usable answer, and SUNAT may have received the document (timeout,
    /// HTTP error, unreadable answer): ask SUNAT before resending.
    Unknown,
    /// The request never reached SUNAT (connection refused, DNS, TLS, rate
    /// limited at SUNAT's front proxy): safe to resend.
    NotDelivered,
}

impl From<Status> for SunatStatus {
    fn from(status: Status) -> Self {
        match status {
            Status::Accepted => Self::Accepted,
            Status::Rejected => Self::Rejected,
            Status::Exception => Self::Exception,
            Status::InProgress => Self::InProgress,
            Status::Unknown => Self::Unknown,
        }
    }
}

#[derive(Serialize, Debug, PartialEq)]
pub struct SendResponse {
    pub status: SunatStatus,
    /// SUNAT's numeric code (CDR ResponseCode or fault code)
    pub code: Option<u32>,
    pub description: Option<String>,
    pub notes: Vec<String>,
    /// CDR zip in base64
    pub cdr_zip: Option<String>,
    pub ticket: Option<String>,
}

impl SendResponse {
    fn new(status: SunatStatus) -> Self {
        Self {
            status,
            code: None,
            description: None,
            notes: Vec::new(),
            cdr_zip: None,
            ticket: None,
        }
    }

    fn with_description(status: SunatStatus, description: impl ToString) -> Self {
        Self {
            description: Some(description.to_string()),
            ..Self::new(status)
        }
    }
}

async fn send(Json(request): Json<SendRequest>) -> Result<Json<SendResponse>, ApiError> {
    if request.environment == Environment::Production && signed_with_demo_key(&request.xml) {
        return Err(ApiError::new(
            ErrorKind::InvalidRequest,
            "the document is signed with SUNAT's beta demo key; SUNAT production would reject it",
        ));
    }
    let credentials = match (request.credentials, request.environment) {
        (Some(c), _) => credentials(c.username, c.password),
        (None, Environment::Beta) => credentials(BETA_USERNAME.into(), BETA_PASSWORD.into()),
        (None, Environment::Production) => {
            return Err(ApiError::new(
                ErrorKind::InvalidRequest,
                "credentials are required in production",
            ))
        }
    };
    let urls = match request.environment {
        Environment::Beta => Urls::beta(),
        Environment::Production => Urls::production(),
    };

    // Everything that can be checked before contacting SUNAT is checked here,
    // so any error after this point happened while talking to SUNAT.
    let file = UblFile::from(request.xml.as_str());
    let metadata = file
        .metadata()
        .map_err(|e| ApiError::new(ErrorKind::InvalidDocument, e))?;
    if metadata.digest_value.is_none() {
        return Err(ApiError::new(
            ErrorKind::InvalidDocument,
            "the document is not signed",
        ));
    }

    let sender = FileSender { urls, credentials };
    let span =
        tracing::info_span!("sunat.send", otel.kind = "client", environment = ?request.environment);
    let result = sender.send_file(&file).instrument(span).await;
    to_send_response(result).map(Json)
}

fn credentials(username: String, password: String) -> Credentials {
    Credentials {
        username,
        password,
        client_id: None,
        client_secret: None,
        token: None,
    }
}

/// Maps xsender's result to the engine's answer. Errors after the request may
/// have reached SUNAT are `Unknown`, never safe to resend blindly
/// (DESIGN.md §9.2.1).
fn to_send_response(
    result: Result<SendFileResponseWrapper, FileSenderError>,
) -> Result<SendResponse, ApiError> {
    let wrapper = match result {
        Ok(wrapper) => wrapper,
        Err(FileSenderError::TargetDiscovery | FileSenderError::NoDespatchUrl) => {
            return Err(ApiError::new(
                ErrorKind::InvalidDocument,
                "SUNAT has no service for this document type in this environment",
            ))
        }
        Err(FileSenderError::SunatClient(error)) => {
            return Ok(match error {
                SunatClientError::HttpClient(e) if e.is_connect() => {
                    SendResponse::with_description(SunatStatus::NotDelivered, e)
                }
                SunatClientError::RateLimited => {
                    SendResponse::with_description(SunatStatus::NotDelivered, error)
                }
                SunatClientError::Templating(e) => {
                    return Err(ApiError::new(ErrorKind::Other, e));
                }
                // Timeouts, HTTP errors, unreadable answers, and any variant
                // xsender adds later: SUNAT may have the document.
                _ => {
                    tracing::warn!(error = %error, "no usable answer from SUNAT");
                    SendResponse::with_description(SunatStatus::Unknown, error)
                }
            });
        }
        // Zip or parsing errors here come from reading SUNAT's answer (the
        // request was checked before sending): SUNAT may have the document.
        Err(error @ (FileSenderError::Zip(_) | FileSenderError::Any(_))) => {
            tracing::warn!(error = %error, "unreadable answer from SUNAT");
            return Ok(SendResponse::with_description(SunatStatus::Unknown, error));
        }
    };

    Ok(match wrapper.response {
        SendFileAggregatedResponse::Cdr(cdr_zip, metadata) => SendResponse {
            status: Status::from_code_str(&metadata.response_code).into(),
            code: metadata.response_code.trim().parse().ok(),
            description: Some(metadata.description),
            notes: metadata.notes,
            cdr_zip: Some(cdr_zip),
            ticket: None,
        },
        SendFileAggregatedResponse::Ticket(ticket) => SendResponse {
            ticket: Some(ticket),
            ..SendResponse::new(SunatStatus::InProgress)
        },
        SendFileAggregatedResponse::Fault(fault) => SendResponse {
            status: fault.status().into(),
            code: fault.sunat_code(),
            description: Some(fault.message),
            ..SendResponse::new(SunatStatus::Unknown)
        },
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::body::Body;
    use axum::http::Request;
    use http_body_util::BodyExt;
    use tower::ServiceExt;

    const INVOICE: &str = include_str!("../../examples/invoice.json");

    async fn post(uri: &str, body: serde_json::Value) -> (StatusCode, serde_json::Value) {
        let request = Request::post(uri)
            .header("content-type", "application/json")
            .body(Body::from(body.to_string()))
            .unwrap();
        let response = router().oneshot(request).await.unwrap();
        let status = response.status();
        let bytes = response.into_body().collect().await.unwrap().to_bytes();
        (status, serde_json::from_slice(&bytes).unwrap())
    }

    fn invoice() -> serde_json::Value {
        serde_json::from_str(INVOICE).unwrap()
    }

    async fn build_invoice() -> serde_json::Value {
        let (status, body) = post(
            "/v1/build",
            serde_json::json!({ "document": invoice(), "fecha_emision": "2026-11-15" }),
        )
        .await;
        assert_eq!(status, StatusCode::OK, "{body}");
        body
    }

    async fn sign_with_demo_key(xml: serde_json::Value) -> serde_json::Value {
        let (status, body) = post(
            "/v1/sign",
            serde_json::json!({ "xml": xml, "demo_key": true }),
        )
        .await;
        assert_eq!(status, StatusCode::OK, "{body}");
        body
    }

    #[tokio::test]
    async fn build_returns_xml_and_enriched_json() {
        let body = build_invoice().await;
        let xml = body["xml"].as_str().unwrap();
        assert!(xml.contains("<cbc:ID>F001-1</cbc:ID>"));
        assert!(xml.contains("<cbc:IssueDate>2026-11-15</cbc:IssueDate>"));
        // Totals are computed by the enrichment pipeline
        assert!(body["enriched_json"]["spec"]["total_importe"].is_object());
        assert!(body["enriched_json"]["spec"]["total_impuestos"].is_object());
    }

    #[tokio::test]
    async fn build_rejects_invalid_document() {
        let mut document = invoice();
        document["spec"]["detalles"][0]["igv_tipo"] = "99".into();
        let (status, body) = post("/v1/build", serde_json::json!({ "document": document })).await;
        assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY);
        assert_eq!(body["kind"], "invalid_document");
    }

    #[tokio::test]
    async fn sign_with_demo_key_returns_digest() {
        let body = sign_with_demo_key(build_invoice().await["xml"].clone()).await;
        let xml = body["xml"].as_str().unwrap();
        assert!(xml.contains("ds:Signature"));
        assert!(signed_with_demo_key(xml));
        assert!(!body["digest_value"].as_str().unwrap().is_empty());
    }

    #[tokio::test]
    async fn sign_requires_an_explicit_key() {
        let (status, body) = post(
            "/v1/sign",
            serde_json::json!({ "xml": build_invoice().await["xml"] }),
        )
        .await;
        assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY);
        assert_eq!(body["kind"], "invalid_request");
    }

    #[tokio::test]
    async fn sign_rejects_wrong_pkcs12_password() {
        let pfx = include_bytes!("../tests/resources/demo.pfx");
        let (status, body) = post(
            "/v1/sign",
            serde_json::json!({
                "xml": build_invoice().await["xml"],
                "pkcs12": {
                    "base64": base64::engine::general_purpose::STANDARD.encode(pfx),
                    "password": "not-the-password",
                },
            }),
        )
        .await;
        assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY);
        assert_eq!(body["kind"], "wrong_password");
    }

    #[tokio::test]
    async fn send_requires_credentials_in_production() {
        let (status, body) = post(
            "/v1/send",
            serde_json::json!({ "xml": "<x/>", "environment": "production" }),
        )
        .await;
        assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY);
        assert_eq!(body["kind"], "invalid_request");
    }

    #[tokio::test]
    async fn send_refuses_demo_key_in_production() {
        let signed = sign_with_demo_key(build_invoice().await["xml"].clone()).await;
        let (status, body) = post(
            "/v1/send",
            serde_json::json!({
                "xml": signed["xml"],
                "environment": "production",
                "credentials": { "username": "20123456789USER", "password": "x" },
            }),
        )
        .await;
        assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY);
        assert_eq!(body["kind"], "invalid_request");
    }

    #[tokio::test]
    async fn send_rejects_unsigned_document_before_contacting_sunat() {
        let (status, body) = post(
            "/v1/send",
            serde_json::json!({ "xml": build_invoice().await["xml"], "environment": "beta" }),
        )
        .await;
        assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY);
        assert_eq!(body["kind"], "invalid_document");
    }

    fn wrapper(response: SendFileAggregatedResponse) -> SendFileResponseWrapper {
        SendFileResponseWrapper {
            response,
            send_file_target: SendFileTarget::Soap(String::new(), SoapFileTargetAction::Bill),
            verify_ticket_target: None,
        }
    }

    fn cdr(code: &str) -> SendFileAggregatedResponse {
        SendFileAggregatedResponse::Cdr(
            "UEsDBA==".into(),
            CdrMetadata {
                response_code: code.into(),
                description: "descripcion".into(),
                notes: vec![],
            },
        )
    }

    #[test]
    fn maps_cdr() {
        let response = to_send_response(Ok(wrapper(cdr("0")))).unwrap();
        assert_eq!(response.status, SunatStatus::Accepted);
        assert_eq!(response.code, Some(0));
        assert_eq!(response.cdr_zip.as_deref(), Some("UEsDBA=="));

        let observed = to_send_response(Ok(wrapper(cdr("4252")))).unwrap();
        assert_eq!(
            (observed.status, observed.code),
            (SunatStatus::Accepted, Some(4252))
        );
        let rejected = to_send_response(Ok(wrapper(cdr("2335")))).unwrap();
        assert_eq!(
            (rejected.status, rejected.code),
            (SunatStatus::Rejected, Some(2335))
        );
    }

    #[test]
    fn maps_ticket() {
        let response = to_send_response(Ok(wrapper(SendFileAggregatedResponse::Ticket(
            "123".into(),
        ))))
        .unwrap();
        assert_eq!(response.status, SunatStatus::InProgress);
        assert_eq!(response.ticket.as_deref(), Some("123"));
    }

    #[test]
    fn maps_fault_codes() {
        let fault = |code: &str| {
            SendFileAggregatedResponse::Fault(SunatFault {
                code: code.into(),
                message: "msg".into(),
                http_status: Some(500),
            })
        };
        let exception = to_send_response(Ok(wrapper(fault("soap-env:Client.0111")))).unwrap();
        assert_eq!(
            (exception.status, exception.code),
            (SunatStatus::Exception, Some(111))
        );
        let rejected = to_send_response(Ok(wrapper(fault("env:Client.2335")))).unwrap();
        assert_eq!(
            (rejected.status, rejected.code),
            (SunatStatus::Rejected, Some(2335))
        );
        let unknown = to_send_response(Ok(wrapper(fault("env:Client")))).unwrap();
        assert_eq!((unknown.status, unknown.code), (SunatStatus::Unknown, None));
    }

    #[test]
    fn errors_after_sending_are_unknown() {
        let status = |error: FileSenderError| to_send_response(Err(error)).unwrap().status;
        assert_eq!(
            status(FileSenderError::SunatClient(SunatClientError::Response(
                503
            ))),
            SunatStatus::Unknown
        );
        assert_eq!(
            status(FileSenderError::SunatClient(SunatClientError::Any(
                anyhow::anyhow!("bad soap")
            ))),
            SunatStatus::Unknown
        );
        assert_eq!(
            status(FileSenderError::Any(anyhow::anyhow!("bad cdr"))),
            SunatStatus::Unknown
        );
    }

    #[test]
    fn rate_limited_is_not_delivered() {
        let response = to_send_response(Err(FileSenderError::SunatClient(
            SunatClientError::RateLimited,
        )))
        .unwrap();
        assert_eq!(response.status, SunatStatus::NotDelivered);
    }

    #[tokio::test]
    async fn connection_refused_is_not_delivered() {
        // Nothing listens on the discard port on localhost
        let error = reqwest::Client::new()
            .get("http://127.0.0.1:9/")
            .send()
            .await
            .unwrap_err();
        let response = to_send_response(Err(FileSenderError::SunatClient(
            SunatClientError::HttpClient(error),
        )))
        .unwrap();
        assert_eq!(response.status, SunatStatus::NotDelivered);
    }

    #[test]
    fn unknown_target_is_invalid_document() {
        let error = to_send_response(Err(FileSenderError::TargetDiscovery)).unwrap_err();
        assert_eq!(error.kind, ErrorKind::InvalidDocument);
    }

    /// Sends a document to SUNAT beta. Needs network access:
    /// `cargo test -- --ignored`.
    #[tokio::test]
    #[ignore]
    async fn send_to_sunat_beta_is_accepted() {
        let mut document = invoice();
        let numero = Utc::now().timestamp() % 99_999_999;
        document["spec"]["serie_numero"] = format!("F001-{numero}").into();
        let (_, built) = post("/v1/build", serde_json::json!({ "document": document })).await;
        let signed = sign_with_demo_key(built["xml"].clone()).await;
        let (status, body) = post(
            "/v1/send",
            serde_json::json!({ "xml": signed["xml"], "environment": "beta" }),
        )
        .await;
        assert_eq!(status, StatusCode::OK, "{body}");
        assert_eq!(body["status"], "accepted", "{body}");
        assert_eq!(body["code"], 0, "{body}");
        assert!(body["cdr_zip"].is_string());
    }
}
