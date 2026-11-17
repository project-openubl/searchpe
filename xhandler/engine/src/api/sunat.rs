//! SUNAT operations: send, tickets, and the consult services (DESIGN.md §9.2).

use std::sync::Arc;

use axum::extract::{Json, State};
use chrono::NaiveDate;
use serde::{Deserialize, Serialize};
use tracing::Instrument;
use xhandler::prelude::*;

use super::{signed_with_demo_key, ApiError, ApiJson, Config, ErrorKind};
use crate::codes::{self, CodeClass};
use crate::consult::{
    self, CdrStatus, FileState, StatusCdrResponse, ValidityResponse, ValidityState,
};

/// SUNAT beta's public SOL user.
const BETA_USERNAME: &str = "12345678959MODDATOS";
const BETA_PASSWORD: &str = "MODDATOS";
/// API client the SUNAT fake accepts for guias de remision (GRE REST)
const FAKE_CLIENT_ID: &str = "xhandler-fake-client";
const FAKE_CLIENT_SECRET: &str = "xhandler-fake-secret";

#[derive(Deserialize, Clone, Copy, Debug, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum Environment {
    Beta,
    Production,
    /// The SUNAT fake (`XHANDLER_SUNAT_FAKE_URL`): production-like, with the
    /// consult services and a GRE endpoint
    Fake,
}

#[derive(Deserialize)]
pub struct SolCredentials {
    pub username: String,
    pub password: String,
    /// API client of the GRE REST services
    pub client_id: Option<String>,
    pub client_secret: Option<String>,
}

/// Replaces the environment's send/ticket URLs, e.g. a third-party OSE
#[derive(Deserialize, Default)]
pub struct UrlsOverride {
    pub invoice: Option<String>,
    pub perception_retention: Option<String>,
    pub despatch: Option<String>,
}

/// Where and as whom to call SUNAT
struct Endpoints {
    urls: Urls,
    consult_url: Option<String>,
    valid_url: Option<String>,
    /// Where GRE tokens are requested; SUNAT's when `None`
    security_api_url: Option<String>,
    credentials: Credentials,
}

impl Endpoints {
    fn resolve(
        config: &Config,
        environment: Environment,
        credentials: Option<SolCredentials>,
        urls: Option<UrlsOverride>,
    ) -> Result<Self, ApiError> {
        let credentials = match (credentials, environment) {
            (Some(c), _) => Credentials {
                username: c.username,
                password: c.password,
                client_id: c.client_id,
                client_secret: c.client_secret,
                token: None,
            },
            (None, Environment::Beta) => Credentials {
                username: BETA_USERNAME.into(),
                password: BETA_PASSWORD.into(),
                ..Default::default()
            },
            (None, Environment::Fake) => Credentials {
                username: BETA_USERNAME.into(),
                password: BETA_PASSWORD.into(),
                client_id: Some(FAKE_CLIENT_ID.into()),
                client_secret: Some(FAKE_CLIENT_SECRET.into()),
                token: None,
            },
            (None, Environment::Production) => {
                return Err(ApiError::new(
                    ErrorKind::InvalidRequest,
                    "credentials are required in production",
                ))
            }
        };
        let mut endpoints = match environment {
            Environment::Beta => Self {
                urls: Urls::beta(),
                consult_url: None,
                valid_url: None,
                security_api_url: None,
                credentials,
            },
            Environment::Production => Self {
                urls: Urls::production(),
                consult_url: Some(BILL_CONSULT_SERVICE_URL.into()),
                valid_url: Some(BILL_VALID_SERVICE_URL.into()),
                security_api_url: None,
                credentials,
            },
            Environment::Fake => {
                let base = config.sunat_fake_url.trim_end_matches('/');
                Self {
                    urls: Urls {
                        invoice: format!("{base}/ol-ti-itcpfegem/billService"),
                        perception_retention: format!(
                            "{base}/ol-ti-itemision-otroscpe-gem/billService"
                        ),
                        despatch: format!("{base}/v1/contribuyente/gem"),
                    },
                    consult_url: Some(format!("{base}/ol-it-wsconscpegem/billConsultService")),
                    valid_url: Some(format!("{base}/ol-it-wsconsvalidcpe/billValidService")),
                    security_api_url: Some(base.to_string()),
                    credentials,
                }
            }
        };
        if let Some(urls) = urls {
            for (slot, url) in [
                (&mut endpoints.urls.invoice, urls.invoice),
                (
                    &mut endpoints.urls.perception_retention,
                    urls.perception_retention,
                ),
                (&mut endpoints.urls.despatch, urls.despatch),
            ] {
                if let Some(url) = url {
                    check_url(&url, environment)?;
                    *slot = url;
                }
            }
        }
        Ok(endpoints)
    }

    fn client(&self) -> SunatClient {
        SunatClient {
            security_api_url: self.security_api_url.clone(),
        }
    }
}

/// A valid URL; in production only https and never a loopback host
fn check_url(value: &str, environment: Environment) -> Result<(), ApiError> {
    let invalid =
        |why: &str| ApiError::new(ErrorKind::InvalidRequest, format!("urls: {value}: {why}"));
    let url = url::Url::parse(value).map_err(|e| invalid(&e.to_string()))?;
    if !matches!(url.scheme(), "http" | "https") {
        return Err(invalid("only http(s) URLs"));
    }
    if environment == Environment::Production {
        if url.scheme() != "https" {
            return Err(invalid("production URLs must be https"));
        }
        let loopback = match url.host() {
            Some(url::Host::Domain(domain)) => {
                let domain = domain.to_ascii_lowercase();
                domain == "localhost" || domain.ends_with(".localhost")
            }
            Some(url::Host::Ipv4(ip)) => ip.is_loopback() || ip.is_unspecified(),
            Some(url::Host::Ipv6(ip)) => ip.is_loopback() || ip.is_unspecified(),
            None => true,
        };
        if loopback {
            return Err(invalid("loopback hosts are not allowed in production"));
        }
    }
    Ok(())
}

// --- responses --------------------------------------------------------------

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
    /// §9.2.3 class of `code` when it is in 99–1999
    pub code_class: Option<CodeClass>,
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
            code_class: None,
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

    fn with_code(status: SunatStatus, code: Option<u32>) -> Self {
        Self {
            code,
            code_class: code.and_then(codes::classify),
            ..Self::new(status)
        }
    }

    fn from_cdr(cdr_zip: String, metadata: CdrMetadata) -> Self {
        Self {
            description: Some(metadata.description),
            notes: metadata.notes,
            cdr_zip: Some(cdr_zip),
            ..Self::with_code(
                Status::from_code_str(&metadata.response_code).into(),
                metadata.response_code.trim().parse().ok(),
            )
        }
    }

    fn from_fault(fault: SunatFault) -> Self {
        Self {
            description: Some(fault.message.clone()),
            ..Self::with_code(fault.status().into(), fault.sunat_code())
        }
    }
}

/// Transport errors: `NotDelivered` only when the request can't have reached
/// SUNAT; anything after it may have arrived is `Unknown` (DESIGN.md §9.2.1).
fn transport_error(error: SunatClientError) -> Result<SunatStatus, ApiError> {
    match error {
        SunatClientError::HttpClient(e) if e.is_connect() => {
            tracing::warn!(error = %e, "SUNAT not reached");
            Ok(SunatStatus::NotDelivered)
        }
        SunatClientError::RateLimited => Ok(SunatStatus::NotDelivered),
        SunatClientError::Templating(e) => Err(ApiError::new(ErrorKind::Other, e)),
        // Timeouts, HTTP errors, unreadable answers, and any variant
        // xsender adds later: SUNAT may have the request.
        error => {
            tracing::warn!(error = %error, "no usable answer from SUNAT");
            Ok(SunatStatus::Unknown)
        }
    }
}

fn describe(error: &impl std::fmt::Display) -> String {
    error.to_string()
}

/// Maps xsender's errors after the request was prepared
fn sender_error(error: FileSenderError) -> Result<SendResponse, ApiError> {
    match error {
        FileSenderError::TargetDiscovery | FileSenderError::NoDespatchUrl => Err(no_service()),
        FileSenderError::SunatClient(error) => {
            let description = describe(&error);
            Ok(SendResponse::with_description(
                transport_error(error)?,
                description,
            ))
        }
        // Zip or parsing errors here come from reading SUNAT's answer (the
        // request was checked before sending): SUNAT may have the document.
        error @ (FileSenderError::Zip(_) | FileSenderError::Any(_)) => {
            tracing::warn!(error = %error, "unreadable answer from SUNAT");
            Ok(SendResponse::with_description(SunatStatus::Unknown, error))
        }
    }
}

fn no_service() -> ApiError {
    ApiError::new(
        ErrorKind::InvalidDocument,
        "SUNAT has no service for this document type in this environment",
    )
}

/// Errors preparing a request (before anything is sent)
fn prepare_error(error: FileSenderError) -> ApiError {
    match error {
        FileSenderError::TargetDiscovery | FileSenderError::NoDespatchUrl => no_service(),
        FileSenderError::Zip(e) => ApiError::new(ErrorKind::Other, e),
        error => ApiError::new(ErrorKind::InvalidDocument, error),
    }
}

/// Maps xsender's result to the engine's answer. Errors after the request may
/// have reached SUNAT are `Unknown`, never safe to resend blindly
/// (DESIGN.md §9.2.1).
pub(crate) fn to_send_response(
    result: Result<SendFileResponseWrapper, FileSenderError>,
) -> Result<SendResponse, ApiError> {
    let wrapper = match result {
        Ok(wrapper) => wrapper,
        Err(error) => return sender_error(error),
    };
    Ok(match wrapper.response {
        SendFileAggregatedResponse::Cdr(cdr_zip, metadata) => {
            SendResponse::from_cdr(cdr_zip, metadata)
        }
        SendFileAggregatedResponse::Ticket(ticket) => SendResponse {
            ticket: Some(ticket),
            ..SendResponse::new(SunatStatus::InProgress)
        },
        SendFileAggregatedResponse::Fault(fault) => SendResponse::from_fault(fault),
    })
}

/// Maps a ticket answer. 98 is in progress; for 99 (and 0) the outcome is
/// the code inside the CDR, not the ticket's (DESIGN.md §9.2.1).
pub(crate) fn to_ticket_response(
    result: Result<VerifyTicketAggregatedResponse, FileSenderError>,
) -> Result<SendResponse, ApiError> {
    Ok(match result {
        Err(error) => return sender_error(error),
        Ok(VerifyTicketAggregatedResponse::Cdr(status, metadata)) => {
            SendResponse::from_cdr(status.cdr_base64, metadata)
        }
        Ok(VerifyTicketAggregatedResponse::NoCdr { status_code }) => SendResponse::with_code(
            Status::from_code_str(&status_code).into(),
            status_code.trim().parse().ok(),
        ),
        Ok(VerifyTicketAggregatedResponse::Fault(fault)) => SendResponse::from_fault(fault),
    })
}

// --- send -------------------------------------------------------------------

#[derive(Deserialize)]
pub struct SendRequest {
    pub xml: String,
    pub environment: Environment,
    /// SOL credentials. Required in production; beta and the fake use
    /// SUNAT's public user.
    pub credentials: Option<SolCredentials>,
    pub urls: Option<UrlsOverride>,
}

pub(super) async fn send(
    State(config): State<Arc<Config>>,
    ApiJson(request): ApiJson<SendRequest>,
) -> Result<Json<SendResponse>, ApiError> {
    if request.environment == Environment::Production && signed_with_demo_key(&request.xml) {
        return Err(ApiError::new(
            ErrorKind::InvalidRequest,
            "the document is signed with SUNAT's beta demo key; SUNAT production would reject it",
        ));
    }
    let endpoints = Endpoints::resolve(
        &config,
        request.environment,
        request.credentials,
        request.urls,
    )?;

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
    check_gre_credentials(&metadata, &endpoints.credentials)?;
    let prepared = prepare_send_file(&file, &endpoints.urls).map_err(prepare_error)?;

    let span = tracing::info_span!("sunat.send", otel.kind = "client", environment = ?request.environment, document_type = %metadata.document_type);
    let result = endpoints
        .client()
        .send_file(
            &prepared.send_file_target,
            &prepared.file,
            &endpoints.credentials,
        )
        .instrument(span)
        .await
        .map_err(FileSenderError::from)
        .and_then(|response| {
            Ok(SendFileResponseWrapper {
                response: response.try_into()?,
                send_file_target: prepared.send_file_target,
                verify_ticket_target: prepared.verify_ticket_target,
            })
        });
    to_send_response(result).map(Json)
}

/// GRE goes through SUNAT's REST API, which needs an API client
fn check_gre_credentials(
    metadata: &UblMetadata,
    credentials: &Credentials,
) -> Result<(), ApiError> {
    let has = |value: &Option<String>| value.as_deref().is_some_and(|v| !v.is_empty());
    if metadata.document_type == "DespatchAdvice"
        && !(has(&credentials.client_id) && has(&credentials.client_secret))
    {
        return Err(ApiError::new(
            ErrorKind::InvalidRequest,
            "guias de remision need credentials.client_id and credentials.client_secret",
        ));
    }
    Ok(())
}

// --- verify-ticket ----------------------------------------------------------

#[derive(Deserialize)]
pub struct VerifyTicketRequest {
    /// The summary, voided documents or GRE that got the ticket: it tells
    /// which service answers the ticket
    pub xml: String,
    pub ticket: String,
    pub environment: Environment,
    pub credentials: Option<SolCredentials>,
    pub urls: Option<UrlsOverride>,
}

pub(super) async fn verify_ticket(
    State(config): State<Arc<Config>>,
    ApiJson(request): ApiJson<VerifyTicketRequest>,
) -> Result<Json<SendResponse>, ApiError> {
    if request.ticket.trim().is_empty() {
        return Err(ApiError::new(ErrorKind::InvalidRequest, "ticket is empty"));
    }
    let endpoints = Endpoints::resolve(
        &config,
        request.environment,
        request.credentials,
        request.urls,
    )?;
    let file = UblFile::from(request.xml.as_str());
    let metadata = file
        .metadata()
        .map_err(|e| ApiError::new(ErrorKind::InvalidDocument, e))?;
    check_gre_credentials(&metadata, &endpoints.credentials)?;
    let target = file_verify_ticket_target(&file, &endpoints.urls).map_err(prepare_error)?;

    let span = tracing::info_span!("sunat.verify_ticket", otel.kind = "client", environment = ?request.environment, document_type = %metadata.document_type);
    let result = endpoints
        .client()
        .verify_ticket(&target, request.ticket.trim(), &endpoints.credentials)
        .instrument(span)
        .await
        .map_err(FileSenderError::from)
        .and_then(|response| response.try_into());
    to_ticket_response(result).map(Json)
}

// --- consult services -------------------------------------------------------

/// A document already sent, as the consult services identify it
#[derive(Deserialize)]
pub struct DocumentKey {
    pub ruc: String,
    /// Catalog 1, e.g. "01"
    pub tipo_comprobante: String,
    pub serie: String,
    pub numero: u32,
}

impl From<DocumentKey> for ConsultDocument {
    fn from(key: DocumentKey) -> Self {
        ConsultDocument {
            ruc: key.ruc,
            tipo_comprobante: key.tipo_comprobante,
            serie: key.serie,
            numero: key.numero,
        }
    }
}

fn no_consult_service() -> ApiError {
    ApiError::new(
        ErrorKind::InvalidRequest,
        "SUNAT beta has no consult service",
    )
}

#[derive(Deserialize)]
pub struct StatusCdrRequest {
    pub environment: Environment,
    pub credentials: Option<SolCredentials>,
    pub document: DocumentKey,
}

pub(super) async fn status_cdr(
    State(config): State<Arc<Config>>,
    ApiJson(request): ApiJson<StatusCdrRequest>,
) -> Result<Json<StatusCdrResponse>, ApiError> {
    let endpoints = Endpoints::resolve(&config, request.environment, request.credentials, None)?;
    let url = endpoints
        .consult_url
        .clone()
        .ok_or_else(no_consult_service)?;
    let span = tracing::info_span!("sunat.consult", otel.kind = "client", operation = "getStatusCdr", environment = ?request.environment);
    let result = endpoints
        .client()
        .get_status_cdr(&url, &request.document.into(), &endpoints.credentials)
        .instrument(span)
        .await;
    Ok(Json(match result {
        Ok(response) => consult::status_cdr(response),
        Err(error) => {
            let description = describe(&error);
            StatusCdrResponse {
                status: match transport_error(error)? {
                    SunatStatus::NotDelivered => CdrStatus::NotDelivered,
                    _ => CdrStatus::Unknown,
                },
                code: None,
                description: Some(description),
                cdr_zip: None,
            }
        }
    }))
}

#[derive(Deserialize)]
pub struct ValidityRequest {
    pub environment: Environment,
    pub credentials: Option<SolCredentials>,
    pub document: DocumentKey,
    /// Catalog 6 code of the buyer's document
    pub receptor_tipo: String,
    pub receptor_numero: String,
    pub fecha_emision: NaiveDate,
    pub importe_total: String,
}

pub(super) async fn validity(
    State(config): State<Arc<Config>>,
    ApiJson(request): ApiJson<ValidityRequest>,
) -> Result<Json<ValidityResponse<ValidityState>>, ApiError> {
    let endpoints = Endpoints::resolve(&config, request.environment, request.credentials, None)?;
    let url = endpoints.valid_url.clone().ok_or_else(no_consult_service)?;
    let cdp = ValidateCdp {
        document: request.document.into(),
        tipo_documento_identidad_receptor: request.receptor_tipo,
        numero_documento_identidad_receptor: request.receptor_numero,
        fecha_emision: request.fecha_emision.format("%d/%m/%Y").to_string(),
        importe_total: request.importe_total,
        numero_autorizacion: None,
    };
    let span = tracing::info_span!("sunat.consult", otel.kind = "client", operation = "validaCDPcriterios", environment = ?request.environment);
    let result = endpoints
        .client()
        .validate_cdp(&url, &cdp, &endpoints.credentials)
        .instrument(span)
        .await;
    Ok(Json(match result {
        Ok(response) => consult::validity(response),
        Err(error) => {
            let description = describe(&error);
            ValidityResponse {
                state: match transport_error(error)? {
                    SunatStatus::NotDelivered => ValidityState::NotDelivered,
                    _ => ValidityState::Unknown,
                },
                code: None,
                description: Some(description),
            }
        }
    }))
}

#[derive(Deserialize)]
pub struct ValidityFileRequest {
    pub xml: String,
    pub environment: Environment,
    pub credentials: Option<SolCredentials>,
}

pub(super) async fn validity_file(
    State(config): State<Arc<Config>>,
    ApiJson(request): ApiJson<ValidityFileRequest>,
) -> Result<Json<ValidityResponse<FileState>>, ApiError> {
    let endpoints = Endpoints::resolve(&config, request.environment, request.credentials, None)?;
    let url = endpoints.valid_url.clone().ok_or_else(no_consult_service)?;
    // The zip as SUNAT names it; production URLs only give the file name
    let file = UblFile::from(request.xml.as_str());
    let prepared = prepare_send_file(&file, &Urls::production()).map_err(prepare_error)?;
    let span = tracing::info_span!("sunat.consult", otel.kind = "client", operation = "verificaCPEarchivo", environment = ?request.environment);
    let result = endpoints
        .client()
        .validate_file(&url, &prepared.file, &endpoints.credentials)
        .instrument(span)
        .await;
    Ok(Json(match result {
        Ok(response) => consult::validity_file(response),
        Err(error) => {
            let description = describe(&error);
            ValidityResponse {
                state: match transport_error(error)? {
                    SunatStatus::NotDelivered => FileState::NotDelivered,
                    _ => FileState::Unknown,
                },
                code: None,
                description: Some(description),
            }
        }
    }))
}

#[cfg(test)]
mod tests {
    use super::super::tests::{build_invoice, invoice, post, sign_with_demo_key};
    use super::*;
    use axum::http::StatusCode;
    use chrono::Utc;

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

    #[test]
    fn production_urls_must_be_https_and_not_loopback() {
        let check = |url: &str| check_url(url, Environment::Production).is_ok();
        assert!(check("https://ose.example.com/ol-ti-itcpfegem/billService"));
        assert!(!check("http://ose.example.com/billService"));
        assert!(!check("https://localhost/billService"));
        assert!(!check("https://127.0.0.1:8443/billService"));
        assert!(!check("https://[::1]/billService"));
        assert!(!check("not a url"));
        assert!(check_url("http://127.0.0.1:8099/billService", Environment::Beta).is_ok());
        assert!(check_url("ftp://x/y", Environment::Fake).is_err());
    }

    #[tokio::test]
    async fn send_refuses_plain_http_urls_in_production() {
        let (status, body) = post(
            "/v1/send",
            serde_json::json!({
                "xml": "<x/>",
                "environment": "production",
                "credentials": { "username": "20123456789USER", "password": "x" },
                "urls": { "invoice": "http://ose.example.com/billService" },
            }),
        )
        .await;
        assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY);
        assert_eq!(body["kind"], "invalid_request");
    }

    #[tokio::test]
    async fn beta_has_no_consult_services() {
        let document = serde_json::json!({ "ruc": "12345678912", "tipo_comprobante": "01", "serie": "F001", "numero": 1 });
        let (status, body) = post(
            "/v1/status-cdr",
            serde_json::json!({ "environment": "beta", "document": document }),
        )
        .await;
        assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY);
        assert_eq!(body["kind"], "invalid_request");
        let (status, _) = post(
            "/v1/validity",
            serde_json::json!({
                "environment": "beta", "document": document, "receptor_tipo": "6",
                "receptor_numero": "12121212121", "fecha_emision": "2026-11-15", "importe_total": "118.00",
            }),
        )
        .await;
        assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY);
        let (status, _) = post(
            "/v1/validity/file",
            serde_json::json!({ "environment": "beta", "xml": "<x/>" }),
        )
        .await;
        assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY);
    }

    fn wrapper(response: SendFileAggregatedResponse) -> SendFileResponseWrapper {
        SendFileResponseWrapper {
            response,
            send_file_target: SendFileTarget::Soap(String::new(), SoapFileTargetAction::Bill),
            verify_ticket_target: None,
        }
    }

    fn metadata(code: &str) -> CdrMetadata {
        CdrMetadata {
            response_code: code.into(),
            description: "descripcion".into(),
            notes: vec![],
        }
    }

    fn cdr(code: &str) -> SendFileAggregatedResponse {
        SendFileAggregatedResponse::Cdr("UEsDBA==".into(), metadata(code))
    }

    #[test]
    fn maps_cdr() {
        let response = to_send_response(Ok(wrapper(cdr("0")))).unwrap();
        assert_eq!(response.status, SunatStatus::Accepted);
        assert_eq!(response.code, Some(0));
        assert_eq!(response.code_class, None);
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

    fn fault(code: &str) -> SunatFault {
        SunatFault {
            code: code.into(),
            message: "msg".into(),
            http_status: Some(500),
        }
    }

    #[test]
    fn maps_fault_codes() {
        let fault = |code: &str| SendFileAggregatedResponse::Fault(fault(code));
        let exception = to_send_response(Ok(wrapper(fault("soap-env:Client.0111")))).unwrap();
        assert_eq!(
            (exception.status, exception.code, exception.code_class),
            (
                SunatStatus::Exception,
                Some(111),
                Some(CodeClass::PermanentCredentials)
            )
        );
        let collision = to_send_response(Ok(wrapper(fault("soap-env:Client.1033")))).unwrap();
        assert_eq!(collision.code_class, Some(CodeClass::AlreadyRegistered));
        let rejected = to_send_response(Ok(wrapper(fault("env:Client.2335")))).unwrap();
        assert_eq!(
            (rejected.status, rejected.code, rejected.code_class),
            (SunatStatus::Rejected, Some(2335), None)
        );
        let unknown = to_send_response(Ok(wrapper(fault("env:Client")))).unwrap();
        assert_eq!((unknown.status, unknown.code), (SunatStatus::Unknown, None));
    }

    #[test]
    fn maps_ticket_answers() {
        let in_progress = to_ticket_response(Ok(VerifyTicketAggregatedResponse::NoCdr {
            status_code: "98".into(),
        }))
        .unwrap();
        assert_eq!(
            (in_progress.status, in_progress.code),
            (SunatStatus::InProgress, Some(98))
        );
        // 99 with a CDR: the CDR's code decides
        let rejected = to_ticket_response(Ok(VerifyTicketAggregatedResponse::Cdr(
            VerifyTicketStatus {
                cdr_base64: "UEsDBA==".into(),
                status_code: "99".into(),
            },
            metadata("2223"),
        )))
        .unwrap();
        assert_eq!(
            (rejected.status, rejected.code),
            (SunatStatus::Rejected, Some(2223))
        );
        let lost = to_ticket_response(Ok(VerifyTicketAggregatedResponse::Fault(fault(
            "S:Client.0127",
        ))))
        .unwrap();
        assert_eq!(
            (lost.status, lost.code_class),
            (SunatStatus::Exception, Some(CodeClass::TicketLost))
        );
        let gone = to_ticket_response(Err(FileSenderError::SunatClient(
            SunatClientError::Response(502),
        )))
        .unwrap();
        assert_eq!(gone.status, SunatStatus::Unknown);
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
