//! HTTP API of the engine (DESIGN.md §4.1). Stateless: every request carries
//! what it needs, and nothing is stored. The only state is [`Config`], read
//! once at startup and never changed.

mod documents;
mod ose;
mod sire;
mod sunat;

use std::sync::Arc;

use axum::extract::{DefaultBodyLimit, FromRequest, Json, Request};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::routing::{get, post};
use axum::Router;
use base64::Engine as _;
use chrono::{FixedOffset, NaiveDate, Utc};
use serde::de::DeserializeOwned;
use serde::{Deserialize, Serialize};
use xhandler::prelude::*;

pub use sunat::{Environment, SendResponse, SunatStatus};

/// SUNAT's public demo key and certificate, accepted only by SUNAT beta.
pub(crate) const BETA_PRIVATE_KEY: &str = include_str!("../resources/beta/private.key");
pub(crate) const BETA_CERTIFICATE: &str = include_str!("../resources/beta/public.cer");

/// Peru has no daylight saving time: America/Lima is always UTC-5.
const LIMA_OFFSET_SECONDS: i32 = 5 * 3600;

const MAX_BODY_BYTES: usize = 10 * 1024 * 1024;

/// Where the engine reaches the SUNAT fake (`environment: "fake"`)
pub const DEFAULT_SUNAT_FAKE_URL: &str = "http://127.0.0.1:8099";

/// Startup configuration, immutable afterwards
#[derive(Clone, Debug)]
pub struct Config {
    /// Base URL of the SUNAT fake: `XHANDLER_SUNAT_FAKE_URL`
    pub sunat_fake_url: String,
}

impl Config {
    pub fn from_env() -> Self {
        Self {
            sunat_fake_url: std::env::var("XHANDLER_SUNAT_FAKE_URL")
                .unwrap_or_else(|_| DEFAULT_SUNAT_FAKE_URL.to_string()),
        }
    }
}

pub fn router() -> Router {
    router_with(Config::from_env())
}

pub fn router_with(config: Config) -> Router {
    Router::new()
        .route("/health", get(|| async { "ok" }))
        .route("/v1/info", get(info))
        .route("/v1/build", post(build))
        .route("/v1/sign", post(sign))
        .route("/v1/sign/prepare", post(sign_prepare))
        .route("/v1/sign/finalize", post(sign_finalize))
        .route("/v1/send", post(sunat::send))
        .route("/v1/verify-ticket", post(sunat::verify_ticket))
        .route("/v1/status-cdr", post(sunat::status_cdr))
        .route("/v1/validity", post(sunat::validity))
        .route("/v1/validity/file", post(sunat::validity_file))
        .route("/v1/validate", post(documents::validate))
        .route("/v1/inspect", post(documents::inspect))
        .route("/v1/cdr", post(documents::cdr))
        .route("/v1/ose/forward", post(ose::forward))
        .route("/v1/sire/proposal", post(sire::proposal))
        .route("/v1/sire/ticket", post(sire::ticket))
        .route(
            "/v1/sire/replace",
            // A register's replacement can be larger than a document
            post(sire::replace).layer(DefaultBodyLimit::max(4 * MAX_BODY_BYTES)),
        )
        .route("/v1/conformity", post(sire::conformity))
        .layer(DefaultBodyLimit::max(MAX_BODY_BYTES))
        .with_state(Arc::new(config))
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
    pub(crate) fn new(kind: ErrorKind, message: impl ToString) -> Self {
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

/// `Json` whose rejections (bad JSON, missing fields) are `invalid_request`
/// errors in the engine's format
pub struct ApiJson<T>(pub T);

impl<T: DeserializeOwned, S: Send + Sync> FromRequest<S> for ApiJson<T> {
    type Rejection = ApiError;

    async fn from_request(request: Request, state: &S) -> Result<Self, Self::Rejection> {
        match Json::<T>::from_request(request, state).await {
            Ok(Json(value)) => Ok(Self(value)),
            Err(rejection) => Err(ApiError::new(
                ErrorKind::InvalidRequest,
                rejection.body_text(),
            )),
        }
    }
}

// --- info -------------------------------------------------------------------

#[derive(Serialize)]
pub struct InfoResponse {
    pub engine_version: &'static str,
    /// The xhandler-rust revision pinned in Cargo.toml (build.rs)
    pub xhandler_rust_rev: &'static str,
}

async fn info() -> Json<InfoResponse> {
    Json(InfoResponse {
        engine_version: env!("CARGO_PKG_VERSION"),
        xhandler_rust_rev: env!("XHANDLER_RUST_REV"),
    })
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

async fn build(ApiJson(request): ApiJson<BuildRequest>) -> Result<Json<BuildResponse>, ApiError> {
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

pub(crate) fn lima_offset() -> FixedOffset {
    FixedOffset::west_opt(LIMA_OFFSET_SECONDS).expect("valid offset")
}

pub(crate) fn lima_today() -> NaiveDate {
    Utc::now().with_timezone(&lima_offset()).date_naive()
}

// --- sign -------------------------------------------------------------------
//
// Real keys never reach the engine: they stay in the HSM, and signing takes
// two calls (DESIGN.md §4.1). The app asks for the data to sign, signs it in
// the HSM, and sends the signature back. The engine is stateless, so finalize
// prepares the document again (preparing is deterministic).

#[derive(Deserialize)]
pub struct SignPrepareRequest {
    pub xml: String,
}

#[derive(Serialize)]
pub struct SignPrepareResponse {
    /// Bytes to sign with RSA PKCS#1 v1.5 + SHA-256 (Java `SHA256withRSA`), base64
    pub data_to_sign: String,
    pub digest_value: String,
}

async fn sign_prepare(
    ApiJson(request): ApiJson<SignPrepareRequest>,
) -> Result<Json<SignPrepareResponse>, ApiError> {
    blocking(move || {
        let prepared = prepare(request.xml)?;
        Ok(SignPrepareResponse {
            data_to_sign: base64::engine::general_purpose::STANDARD.encode(prepared.data_to_sign()),
            digest_value: prepared.digest_value().to_string(),
        })
    })
    .await
}

#[derive(Deserialize)]
pub struct SignFinalizeRequest {
    pub xml: String,
    /// Signature over `data_to_sign`, base64
    pub signature_value: String,
    /// Signer's X.509 certificate (DER), base64
    pub certificate: String,
}

#[derive(Serialize)]
pub struct SignResponse {
    pub xml: String,
    pub digest_value: String,
}

async fn sign_finalize(
    ApiJson(request): ApiJson<SignFinalizeRequest>,
) -> Result<Json<SignResponse>, ApiError> {
    blocking(move || {
        let decode = |value: &str, what: &str| {
            base64::engine::general_purpose::STANDARD
                .decode(value)
                .map_err(|e| ApiError::new(ErrorKind::InvalidRequest, format!("{what}: {e}")))
        };
        let signature_value = decode(&request.signature_value, "signature_value")?;
        let certificate = decode(&request.certificate, "certificate")?;
        let prepared = prepare(request.xml)?;
        let digest_value = prepared.digest_value().to_string();
        // Fails when the signature doesn't verify with the certificate (wrong key)
        let signed = prepared
            .finalize(&signature_value, &certificate)
            .map_err(|e| ApiError::new(ErrorKind::InvalidRequest, e))?;
        let xml = String::from_utf8(signed).map_err(|e| ApiError::new(ErrorKind::Other, e))?;
        Ok(SignResponse { xml, digest_value })
    })
    .await
}

/// One-step signing with SUNAT's public beta demo key, for beta companies
/// without their own certificate (DESIGN.md §4.1). Must be explicit.
#[derive(Deserialize)]
pub struct SignRequest {
    pub xml: String,
    #[serde(default)]
    pub demo_key: bool,
}

async fn sign(ApiJson(request): ApiJson<SignRequest>) -> Result<Json<SignResponse>, ApiError> {
    if !request.demo_key {
        return Err(ApiError::new(
            ErrorKind::InvalidRequest,
            "one-step signing is only for SUNAT's beta demo key (demo_key: true); use /v1/sign/prepare and /v1/sign/finalize",
        ));
    }
    blocking(move || {
        let key_pair = RsaKeyPair::from_pem(BETA_PRIVATE_KEY, BETA_CERTIFICATE)
            .map_err(|e| ApiError::new(ErrorKind::Other, e))?;
        let prepared = prepare(request.xml)?;
        let digest_value = prepared.digest_value().to_string();
        let signature_value = key_pair
            .sign(prepared.data_to_sign())
            .map_err(|e| ApiError::new(ErrorKind::Other, e))?;
        let signed = prepared
            .finalize(&signature_value, key_pair.certificate_der())
            .map_err(|e| ApiError::new(ErrorKind::Other, e))?;
        let xml = String::from_utf8(signed).map_err(|e| ApiError::new(ErrorKind::Other, e))?;
        Ok(SignResponse { xml, digest_value })
    })
    .await
}

fn prepare(xml: String) -> Result<PreparedSignature, ApiError> {
    XSigner { xml_document: xml }
        .prepare()
        .map_err(|e| ApiError::new(ErrorKind::InvalidDocument, e))
}

/// Runs CPU-bound work (C14N, RSA) off the async workers, in the caller's span.
pub(crate) async fn blocking<T: Send + 'static>(
    work: impl FnOnce() -> Result<T, ApiError> + Send + 'static,
) -> Result<Json<T>, ApiError> {
    let span = tracing::Span::current();
    tokio::task::spawn_blocking(move || span.in_scope(work))
        .await
        .map_err(|e| ApiError::new(ErrorKind::Other, e))?
        .map(Json)
}

/// True when the XML is signed with SUNAT's beta demo certificate.
pub(crate) fn signed_with_demo_key(xml: &str) -> bool {
    let certificate: String = BETA_CERTIFICATE
        .lines()
        .filter(|line| !line.starts_with("-----"))
        .collect();
    let xml: String = xml.chars().filter(|c| !c.is_whitespace()).collect();
    xml.contains(&certificate)
}

#[cfg(test)]
pub(crate) mod tests {
    use super::*;
    use axum::body::Body;
    use axum::http::Request;
    use http_body_util::BodyExt;
    use tower::ServiceExt;

    const INVOICE: &str = include_str!("../../examples/invoice.json");

    pub async fn post(uri: &str, body: serde_json::Value) -> (StatusCode, serde_json::Value) {
        let request = Request::post(uri)
            .header("content-type", "application/json")
            .body(Body::from(body.to_string()))
            .unwrap();
        let response = router().oneshot(request).await.unwrap();
        let status = response.status();
        let bytes = response.into_body().collect().await.unwrap().to_bytes();
        (status, serde_json::from_slice(&bytes).unwrap())
    }

    pub fn invoice() -> serde_json::Value {
        serde_json::from_str(INVOICE).unwrap()
    }

    pub async fn build_invoice() -> serde_json::Value {
        let (status, body) = post(
            "/v1/build",
            serde_json::json!({ "document": invoice(), "fecha_emision": "2026-11-15" }),
        )
        .await;
        assert_eq!(status, StatusCode::OK, "{body}");
        body
    }

    pub async fn sign_with_demo_key(xml: serde_json::Value) -> serde_json::Value {
        let (status, body) = post(
            "/v1/sign",
            serde_json::json!({ "xml": xml, "demo_key": true }),
        )
        .await;
        assert_eq!(status, StatusCode::OK, "{body}");
        body
    }

    #[tokio::test]
    async fn info_reports_versions() {
        let request = Request::get("/v1/info").body(Body::empty()).unwrap();
        let response = router().oneshot(request).await.unwrap();
        let bytes = response.into_body().collect().await.unwrap().to_bytes();
        let body: serde_json::Value = serde_json::from_slice(&bytes).unwrap();
        assert_eq!(body["engine_version"], env!("CARGO_PKG_VERSION"));
        let rev = body["xhandler_rust_rev"].as_str().unwrap();
        assert_eq!(rev.len(), 40, "{rev}");
        assert!(include_str!("../Cargo.toml").contains(&format!("rev = \"{rev}\"")));
    }

    #[tokio::test]
    async fn bad_json_is_an_invalid_request() {
        let (status, body) = post("/v1/sign", serde_json::json!({ "demo_key": true })).await;
        assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY);
        assert_eq!(body["kind"], "invalid_request");
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

    /// Two-step signing with an external key gives the same document as the
    /// one-step demo signing (same key).
    #[tokio::test]
    async fn prepare_and_finalize_match_one_step_signing() {
        let xml = build_invoice().await["xml"].clone();
        let (status, prepared) = post("/v1/sign/prepare", serde_json::json!({ "xml": xml })).await;
        assert_eq!(status, StatusCode::OK, "{prepared}");

        // The "HSM": any RSA PKCS#1 v1.5 SHA-256 signer
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
        assert_eq!(finalized["digest_value"], prepared["digest_value"]);

        let one_step = sign_with_demo_key(xml).await;
        assert_eq!(finalized["xml"], one_step["xml"]);
    }

    #[tokio::test]
    async fn finalize_rejects_a_signature_that_does_not_match_the_certificate() {
        let xml = build_invoice().await["xml"].clone();
        let key_pair = RsaKeyPair::from_pem(BETA_PRIVATE_KEY, BETA_CERTIFICATE).unwrap();
        // A valid RSA signature, but over other data
        let signature = key_pair.sign(b"other data").unwrap();
        let (status, body) = post(
            "/v1/sign/finalize",
            serde_json::json!({
                "xml": xml,
                "signature_value": base64::engine::general_purpose::STANDARD.encode(signature),
                "certificate": base64::engine::general_purpose::STANDARD.encode(key_pair.certificate_der()),
            }),
        )
        .await;
        assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY, "{body}");
        assert_eq!(body["kind"], "invalid_request");
    }

    #[tokio::test]
    async fn finalize_rejects_bad_base64() {
        let (status, body) = post(
            "/v1/sign/finalize",
            serde_json::json!({
                "xml": build_invoice().await["xml"],
                "signature_value": "not base64!",
                "certificate": "",
            }),
        )
        .await;
        assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY);
        assert_eq!(body["kind"], "invalid_request");
    }
}
