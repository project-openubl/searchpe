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
    InvalidDocument,
    InvalidCertificate,
    WrongPassword,
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
            ErrorKind::InvalidDocument
            | ErrorKind::InvalidCertificate
            | ErrorKind::WrongPassword => StatusCode::UNPROCESSABLE_ENTITY,
            ErrorKind::Other => StatusCode::INTERNAL_SERVER_ERROR,
        };
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
    /// PKCS#12 keystore. Without it, SUNAT's beta demo key is used.
    /// Temporary until the HSM two-step signing of R1 (DESIGN.md §19).
    pub pkcs12: Option<Pkcs12>,
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
    tokio::task::spawn_blocking(move || sign_blocking(request))
        .await
        .map_err(|e| ApiError::new(ErrorKind::Other, e))?
        .map(Json)
}

fn sign_blocking(request: SignRequest) -> Result<SignResponse, ApiError> {
    let key_pair = match &request.pkcs12 {
        Some(pkcs12) => {
            let bytes = base64::engine::general_purpose::STANDARD
                .decode(&pkcs12.base64)
                .map_err(|e| ApiError::new(ErrorKind::InvalidCertificate, e))?;
            RsaKeyPair::from_pkcs12(&bytes, &pkcs12.password).map_err(|e| match e {
                SignError::WrongPassword => ApiError::new(ErrorKind::WrongPassword, e),
                _ => ApiError::new(ErrorKind::InvalidCertificate, e),
            })?
        }
        None => RsaKeyPair::from_pem(BETA_PRIVATE_KEY, BETA_CERTIFICATE)
            .map_err(|e| ApiError::new(ErrorKind::Other, e))?,
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

/// xsender's `Status` plus `NotDelivered`: SUNAT gave no usable answer.
/// The service decides what each outcome means (DESIGN.md §9.2).
#[derive(Serialize, Debug, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum SunatStatus {
    Accepted,
    Rejected,
    Exception,
    InProgress,
    Unknown,
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

#[derive(Serialize, Debug)]
pub struct SendResponse {
    pub status: SunatStatus,
    pub code: Option<String>,
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
}

async fn send(Json(request): Json<SendRequest>) -> Result<Json<SendResponse>, ApiError> {
    let credentials = match (request.credentials, request.environment) {
        (Some(c), _) => Credentials {
            username: c.username,
            password: c.password,
            client_id: None,
            client_secret: None,
            token: None,
        },
        (None, Environment::Beta) => Credentials {
            username: BETA_USERNAME.to_string(),
            password: BETA_PASSWORD.to_string(),
            client_id: None,
            client_secret: None,
            token: None,
        },
        (None, Environment::Production) => {
            return Err(ApiError::new(
                ErrorKind::Other,
                "credentials are required in production",
            ))
        }
    };
    let urls = match request.environment {
        Environment::Beta => Urls::beta(),
        Environment::Production => Urls::production(),
    };
    let sender = FileSender { urls, credentials };
    let file = UblFile::from(request.xml.as_str());

    let span =
        tracing::info_span!("sunat.send", otel.kind = "client", environment = ?request.environment);
    let result = sender.send_file(&file).instrument(span).await;

    let response = match result {
        Ok(wrapper) => match wrapper.response {
            SendFileAggregatedResponse::Cdr(cdr_zip, metadata) => SendResponse {
                status: Status::from_code_str(&metadata.response_code).into(),
                code: Some(metadata.response_code),
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
                code: fault.sunat_code().map(|c| format!("{c:04}")),
                description: Some(fault.message),
                ..SendResponse::new(SunatStatus::Unknown)
            },
        },
        // No usable answer from SUNAT (connection, timeout, HTTP error...).
        Err(FileSenderError::SunatClient(e)) => {
            tracing::warn!(error = %e, "SUNAT did not answer");
            SendResponse {
                description: Some(e.to_string()),
                ..SendResponse::new(SunatStatus::NotDelivered)
            }
        }
        // The file itself can't be sent (unknown type, bad zip...).
        Err(e) => return Err(ApiError::new(ErrorKind::InvalidDocument, e)),
    };
    Ok(Json(response))
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
    async fn sign_with_beta_key_returns_digest() {
        let xml = build_invoice().await["xml"].clone();
        let (status, body) = post("/v1/sign", serde_json::json!({ "xml": xml })).await;
        assert_eq!(status, StatusCode::OK, "{body}");
        assert!(body["xml"].as_str().unwrap().contains("ds:Signature"));
        assert!(!body["digest_value"].as_str().unwrap().is_empty());
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
        let (status, _) = post(
            "/v1/send",
            serde_json::json!({ "xml": "<x/>", "environment": "production" }),
        )
        .await;
        assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR);
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
        let (_, signed) = post("/v1/sign", serde_json::json!({ "xml": built["xml"] })).await;
        let (status, body) = post(
            "/v1/send",
            serde_json::json!({ "xml": signed["xml"], "environment": "beta" }),
        )
        .await;
        assert_eq!(status, StatusCode::OK, "{body}");
        assert_eq!(body["status"], "accepted", "{body}");
        assert!(body["cdr_zip"].is_string());
    }
}
