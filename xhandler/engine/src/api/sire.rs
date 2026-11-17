//! SUNAT's REST APIs for tax compliance (DESIGN.md §11.7, §11.8, R2): SIRE
//! (the sales and purchases registers that replaced PLE) and the factura
//! negociable conformity consult.
//!
//! Both authenticate with an OAuth password grant on SUNAT's security API:
//! the tenant's API client (client_id/secret, created in SOL) plus its SOL
//! user. The engine stays stateless: every call requests its own token.
//!
//! SIRE (manual "Servicios web API SIRE"):
//!
//! | Operation                | Request                                                                                       |
//! |--------------------------|-----------------------------------------------------------------------------------------------|
//! | Export RVIE proposal     | `GET /v1/contribuyente/migeigv/libros/rvie/propuesta/web/propuesta/{per}/exportapropuesta?codTipoArchivo=0` → `numTicket` |
//! | Export RCE proposal      | `GET /v1/contribuyente/migeigv/libros/rce/propuesta/web/propuesta/{per}/exportacioncomprobantepropuesta?codTipoArchivo=0&codOrigenEnvio=2` |
//! | Ticket state             | `GET …/rvierce/gestionprocesosmasivos/web/masivo/consultaestadotickets?perIni&perFin&page=1&perPage=20&numTicket` |
//! | Download a ticket's file | `GET …/rvierce/gestionprocesosmasivos/web/masivo/archivoreporte?nomArchivoReporte&codTipoArchivoReporte&codLibro&perTribu&numTicket` |
//! | Replace the proposal     | `POST …/{book}/propuesta/web/propuesta/{per}/reemplazarpropuesta` (multipart: the ZIP and its SHA-256) → `numTicket` |
//!
//! Ticket states: `codEstadoProceso` "06" (Terminado) is done; a description
//! with "error" or "rechaz" is an error; anything else is still pending.
//!
//! Conformity (factura negociable, RS 000165-2021):
//! `GET /v1/contribuyente/facturanegociable/comprobantes/{ruc}-{tipo}-{serie}-{numero}/conformidad`
//! → `estado`: "1" pending, "2" agreed (conforme), "3" objected
//! (disconforme), "4" silent agreement (conformidad presunta after 8 days),
//! "0"/404 not found.
//!
//! SIRE has no beta environment: production, or the SUNAT fake.

use std::sync::Arc;
use std::time::Duration;

use axum::extract::{Json, State};
use base64::{engine::general_purpose::STANDARD, Engine as _};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

use super::sunat::Environment;
use super::{ApiError, ApiJson, Config, ErrorKind};

const SECURITY_URL: &str = "https://api-seguridad.sunat.gob.pe";
const SIRE_URL: &str = "https://api-sire.sunat.gob.pe";
const SIRE_SCOPE: &str = "https://api-sire.sunat.gob.pe";
/// Below the app's read timeout for the engine (timeouts nest)
const TIMEOUT: Duration = Duration::from_secs(30);

#[derive(Deserialize)]
pub struct ApiCredentials {
    pub client_id: String,
    pub client_secret: String,
    /// RUC + SOL user, e.g. 20123456789USUARIO1
    pub username: String,
    pub password: String,
}

#[derive(Deserialize, Clone, Copy, Debug, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum Book {
    /// Registro de Ventas e Ingresos Electrónico
    Rvie,
    /// Registro de Compras Electrónico
    Rce,
}

impl Book {
    fn path(self) -> &'static str {
        match self {
            Book::Rvie => "rvie",
            Book::Rce => "rce",
        }
    }

    fn code(self) -> &'static str {
        match self {
            Book::Rvie => "140000",
            Book::Rce => "080000",
        }
    }
}

struct Apis {
    security: String,
    sire: String,
}

fn apis(config: &Config, environment: Environment) -> Result<Apis, ApiError> {
    match environment {
        Environment::Production => Ok(Apis {
            security: SECURITY_URL.into(),
            sire: SIRE_URL.into(),
        }),
        Environment::Fake => {
            let base = config.sunat_fake_url.trim_end_matches('/').to_string();
            Ok(Apis {
                security: base.clone(),
                sire: base,
            })
        }
        Environment::Beta => Err(ApiError::new(
            ErrorKind::InvalidRequest,
            "SUNAT has no beta environment for SIRE or conformity",
        )),
    }
}

/// Outcome of a call that may not have reached SUNAT
#[derive(Serialize, Debug, PartialEq, Clone, Copy)]
#[serde(rename_all = "snake_case")]
pub enum CallState {
    Ok,
    /// SUNAT refused the credentials (fix them before retrying)
    Unauthorized,
    /// SUNAT answered with an error, or not at all: retry later
    Unavailable,
}

fn client() -> Result<reqwest::Client, ApiError> {
    reqwest::Client::builder()
        .timeout(TIMEOUT)
        .build()
        .map_err(|e| ApiError::new(ErrorKind::Other, e))
}

enum Failure {
    Unauthorized(String),
    Unavailable(String),
}

async fn token(
    http: &reqwest::Client,
    apis: &Apis,
    credentials: &ApiCredentials,
) -> Result<String, Failure> {
    let url = format!(
        "{}/v1/clientessol/{}/oauth2/token/",
        apis.security, credentials.client_id
    );
    let form = [
        ("grant_type", "password"),
        ("scope", SIRE_SCOPE),
        ("client_id", credentials.client_id.as_str()),
        ("client_secret", credentials.client_secret.as_str()),
        ("username", credentials.username.as_str()),
        ("password", credentials.password.as_str()),
    ];
    let response = http
        .post(url)
        .form(&form)
        .send()
        .await
        .map_err(|e| Failure::Unavailable(e.to_string()))?;
    let status = response.status();
    let body = response
        .text()
        .await
        .map_err(|e| Failure::Unavailable(e.to_string()))?;
    if status.is_client_error() {
        return Err(Failure::Unauthorized(format!(
            "SUNAT refused the API credentials: {}",
            truncate(&body)
        )));
    }
    if !status.is_success() {
        return Err(Failure::Unavailable(format!(
            "{status}: {}",
            truncate(&body)
        )));
    }
    let value: serde_json::Value =
        serde_json::from_str(&body).map_err(|e| Failure::Unavailable(e.to_string()))?;
    value["access_token"]
        .as_str()
        .map(str::to_owned)
        .ok_or_else(|| Failure::Unavailable("the token answer has no access_token".into()))
}

async fn get_json(
    http: &reqwest::Client,
    token: &str,
    url: &str,
) -> Result<(u16, serde_json::Value), Failure> {
    let response = http
        .get(url)
        .bearer_auth(token)
        .send()
        .await
        .map_err(|e| Failure::Unavailable(e.to_string()))?;
    let status = response.status().as_u16();
    let body = response
        .text()
        .await
        .map_err(|e| Failure::Unavailable(e.to_string()))?;
    if status == 401 || status == 403 {
        return Err(Failure::Unauthorized(truncate(&body)));
    }
    let value = serde_json::from_str(&body).unwrap_or(serde_json::Value::Null);
    Ok((status, value))
}

fn truncate(text: &str) -> String {
    text.chars().take(300).collect()
}

fn check_period(period: &str) -> Result<(), ApiError> {
    let valid = period.len() == 6
        && period.chars().all(|c| c.is_ascii_digit())
        && matches!(period[4..].parse::<u8>(), Ok(1..=12));
    if valid {
        Ok(())
    } else {
        Err(ApiError::new(
            ErrorKind::InvalidRequest,
            "period must be YYYYMM",
        ))
    }
}

// --- SIRE ---------------------------------------------------------------------

#[derive(Deserialize)]
pub struct ProposalRequest {
    pub environment: Environment,
    pub credentials: ApiCredentials,
    pub book: Book,
    pub period: String,
}

#[derive(Serialize, Debug)]
pub struct TicketResponse {
    pub state: CallState,
    pub ticket: Option<String>,
    pub description: Option<String>,
}

impl TicketResponse {
    fn failed(failure: Failure) -> Self {
        let (state, description) = match failure {
            Failure::Unauthorized(d) => (CallState::Unauthorized, d),
            Failure::Unavailable(d) => (CallState::Unavailable, d),
        };
        Self {
            state,
            ticket: None,
            description: Some(description),
        }
    }
}

/// Asks SUNAT to export its proposal of a register; the file comes with the ticket
pub(super) async fn proposal(
    State(config): State<Arc<Config>>,
    ApiJson(request): ApiJson<ProposalRequest>,
) -> Result<Json<TicketResponse>, ApiError> {
    check_period(&request.period)?;
    let apis = apis(&config, request.environment)?;
    let http = client()?;
    let result = async {
        let token = token(&http, &apis, &request.credentials).await?;
        let url = match request.book {
            Book::Rvie => format!(
                "{}/v1/contribuyente/migeigv/libros/rvie/propuesta/web/propuesta/{}/exportapropuesta?codTipoArchivo=0",
                apis.sire, request.period
            ),
            Book::Rce => format!(
                "{}/v1/contribuyente/migeigv/libros/rce/propuesta/web/propuesta/{}/exportacioncomprobantepropuesta?codTipoArchivo=0&codOrigenEnvio=2",
                apis.sire, request.period
            ),
        };
        let (status, body) = get_json(&http, &token, &url).await?;
        ticket_of(status, &body)
    }
    .await;
    Ok(Json(match result {
        Ok(ticket) => TicketResponse {
            state: CallState::Ok,
            ticket: Some(ticket),
            description: None,
        },
        Err(failure) => TicketResponse::failed(failure),
    }))
}

fn ticket_of(status: u16, body: &serde_json::Value) -> Result<String, Failure> {
    let ticket = match &body["numTicket"] {
        serde_json::Value::String(s) => Some(s.clone()),
        serde_json::Value::Number(n) => Some(n.to_string()),
        _ => None,
    };
    match ticket {
        Some(ticket) if (200..300).contains(&status) => Ok(ticket),
        _ => Err(Failure::Unavailable(format!(
            "SUNAT answered {status} without a ticket: {}",
            truncate(&body.to_string())
        ))),
    }
}

#[derive(Deserialize)]
pub struct SireTicketRequest {
    pub environment: Environment,
    pub credentials: ApiCredentials,
    pub book: Book,
    pub period: String,
    pub ticket: String,
}

#[derive(Serialize, Debug, PartialEq, Clone, Copy)]
#[serde(rename_all = "snake_case")]
pub enum TicketState {
    Pending,
    Done,
    Error,
}

#[derive(Serialize, Debug)]
pub struct SireFile {
    pub name: String,
    pub content_base64: String,
}

#[derive(Serialize, Debug)]
pub struct SireTicketResponse {
    pub state: CallState,
    pub ticket_state: Option<TicketState>,
    pub description: Option<String>,
    /// The ticket's report files once it is done (the proposal, or SUNAT's observations)
    pub files: Vec<SireFile>,
}

/// A ticket's state; when it's done, its files are downloaded too
pub(super) async fn ticket(
    State(config): State<Arc<Config>>,
    ApiJson(request): ApiJson<SireTicketRequest>,
) -> Result<Json<SireTicketResponse>, ApiError> {
    check_period(&request.period)?;
    let apis = apis(&config, request.environment)?;
    let http = client()?;
    let result = async {
        let token = token(&http, &apis, &request.credentials).await?;
        let base = format!(
            "{}/v1/contribuyente/migeigv/libros/rvierce/gestionprocesosmasivos/web/masivo",
            apis.sire
        );
        let url = format!(
            "{base}/consultaestadotickets?perIni={p}&perFin={p}&page=1&perPage=20&numTicket={t}",
            p = request.period,
            t = request.ticket
        );
        let (status, body) = get_json(&http, &token, &url).await?;
        if !(200..300).contains(&status) {
            return Err(Failure::Unavailable(format!(
                "ticket state answered {status}"
            )));
        }
        let (ticket_state, description, reports) = ticket_state(&body, &request.ticket);
        let mut files = Vec::new();
        if ticket_state == TicketState::Done {
            for (name, kind) in reports {
                let url = format!(
                    "{base}/archivoreporte?nomArchivoReporte={name}&codTipoArchivoReporte={kind}&codLibro={libro}&perTribu={p}&codProceso=&numTicket={t}",
                    libro = request.book.code(),
                    p = request.period,
                    t = request.ticket
                );
                let response = http
                    .get(&url)
                    .bearer_auth(&token)
                    .send()
                    .await
                    .map_err(|e| Failure::Unavailable(e.to_string()))?;
                if !response.status().is_success() {
                    return Err(Failure::Unavailable(format!(
                        "file {name} answered {}",
                        response.status()
                    )));
                }
                let bytes = response
                    .bytes()
                    .await
                    .map_err(|e| Failure::Unavailable(e.to_string()))?;
                files.push(SireFile {
                    name,
                    content_base64: STANDARD.encode(bytes),
                });
            }
        }
        Ok((ticket_state, description, files))
    }
    .await;
    Ok(Json(match result {
        Ok((ticket_state, description, files)) => SireTicketResponse {
            state: CallState::Ok,
            ticket_state: Some(ticket_state),
            description,
            files,
        },
        Err(failure) => {
            let failed = TicketResponse::failed(failure);
            SireTicketResponse {
                state: failed.state,
                ticket_state: None,
                description: failed.description,
                files: vec![],
            }
        }
    }))
}

/// (state, description, report files as (name, type code)) of a ticket in
/// the `consultaestadotickets` answer
fn ticket_state(
    body: &serde_json::Value,
    ticket: &str,
) -> (TicketState, Option<String>, Vec<(String, String)>) {
    let registros = body["registros"].as_array().cloned().unwrap_or_default();
    let Some(entry) = registros.iter().find(|r| {
        r["numTicket"].as_str() == Some(ticket)
            || r["numTicket"].as_u64().map(|n| n.to_string()).as_deref() == Some(ticket)
    }) else {
        return (
            TicketState::Pending,
            Some("ticket not listed yet".into()),
            vec![],
        );
    };
    let code = entry["codEstadoProceso"].as_str().unwrap_or_default();
    let description = entry["desEstadoProceso"]
        .as_str()
        .unwrap_or_default()
        .to_string();
    let lower = description.to_lowercase();
    let state = if lower.contains("error") || lower.contains("rechaz") {
        TicketState::Error
    } else if code == "06" || lower.contains("terminado") {
        TicketState::Done
    } else {
        TicketState::Pending
    };
    let reports = entry["archivoReporte"]
        .as_array()
        .map(|files| {
            files
                .iter()
                .filter_map(|f| {
                    Some((
                        f["nomArchivoReporte"].as_str()?.to_string(),
                        f["codTipoAchivoReporte"]
                            .as_str()
                            .or_else(|| f["codTipoArchivoReporte"].as_str())
                            .unwrap_or_default()
                            .to_string(),
                    ))
                })
                .collect()
        })
        .unwrap_or_default();
    (state, Some(description), reports)
}

#[derive(Deserialize)]
pub struct ReplaceRequest {
    pub environment: Environment,
    pub credentials: ApiCredentials,
    pub book: Book,
    pub period: String,
    /// ZIP with the replacement TXT, named as SUNAT expects (LE…)
    pub file_name: String,
    pub zip_base64: String,
}

/// Submits a replacement of SUNAT's proposal: the ZIP and its SHA-256
pub(super) async fn replace(
    State(config): State<Arc<Config>>,
    ApiJson(request): ApiJson<ReplaceRequest>,
) -> Result<Json<TicketResponse>, ApiError> {
    check_period(&request.period)?;
    let zip = STANDARD
        .decode(&request.zip_base64)
        .map_err(|_| ApiError::new(ErrorKind::InvalidRequest, "zip_base64 is not base64"))?;
    if !request.file_name.ends_with(".zip")
        || request.file_name.contains(['/', '\\', '"', '\r', '\n'])
    {
        return Err(ApiError::new(
            ErrorKind::InvalidRequest,
            "file_name must be a plain .zip name",
        ));
    }
    let apis = apis(&config, request.environment)?;
    let http = client()?;
    let digest = hex(&Sha256::digest(&zip));
    let result = async {
        let token = token(&http, &apis, &request.credentials).await?;
        let url = format!(
            "{}/v1/contribuyente/migeigv/libros/{}/propuesta/web/propuesta/{}/reemplazarpropuesta",
            apis.sire,
            request.book.path(),
            request.period
        );
        let (content_type, body) = multipart(&request.file_name, &zip, &digest);
        let response = http
            .post(url)
            .bearer_auth(&token)
            .header(reqwest::header::CONTENT_TYPE, content_type)
            .body(body)
            .send()
            .await
            .map_err(|e| Failure::Unavailable(e.to_string()))?;
        let status = response.status().as_u16();
        let text = response
            .text()
            .await
            .map_err(|e| Failure::Unavailable(e.to_string()))?;
        if status == 401 || status == 403 {
            return Err(Failure::Unauthorized(truncate(&text)));
        }
        ticket_of(
            status,
            &serde_json::from_str(&text).unwrap_or(serde_json::Value::Null),
        )
    }
    .await;
    Ok(Json(match result {
        Ok(ticket) => TicketResponse {
            state: CallState::Ok,
            ticket: Some(ticket),
            description: None,
        },
        Err(failure) => TicketResponse::failed(failure),
    }))
}

/// multipart/form-data with the file and its hash (built by hand: reqwest's
/// multipart feature would add crates for one form)
fn multipart(file_name: &str, zip: &[u8], sha256: &str) -> (String, Vec<u8>) {
    let boundary = format!("xhandler-{}", &sha256[..24]);
    let mut body = Vec::with_capacity(zip.len() + 512);
    body.extend_from_slice(
        format!(
            "--{boundary}\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"{file_name}\"\r\nContent-Type: application/zip\r\n\r\n"
        )
        .as_bytes(),
    );
    body.extend_from_slice(zip);
    body.extend_from_slice(
        format!(
            "\r\n--{boundary}\r\nContent-Disposition: form-data; name=\"nomArchivo\"\r\n\r\n{file_name}\r\n--{boundary}\r\nContent-Disposition: form-data; name=\"hash\"\r\n\r\n{sha256}\r\n--{boundary}--\r\n"
        )
        .as_bytes(),
    );
    (format!("multipart/form-data; boundary={boundary}"), body)
}

fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

// --- conformity ----------------------------------------------------------------

#[derive(Deserialize)]
pub struct ConformityRequest {
    pub environment: Environment,
    pub credentials: ApiCredentials,
    pub ruc: String,
    pub tipo_comprobante: String,
    pub serie: String,
    pub numero: u64,
}

#[derive(Serialize, Debug, PartialEq, Clone, Copy)]
#[serde(rename_all = "snake_case")]
pub enum ConformityState {
    Pending,
    Agreed,
    Objected,
    SilentAgreement,
    NotFound,
    Unknown,
}

#[derive(Serialize, Debug)]
pub struct ConformityResponse {
    pub state: CallState,
    pub conformity: ConformityState,
    pub description: Option<String>,
}

pub(super) async fn conformity(
    State(config): State<Arc<Config>>,
    ApiJson(request): ApiJson<ConformityRequest>,
) -> Result<Json<ConformityResponse>, ApiError> {
    if request.ruc.len() != 11 || !request.ruc.chars().all(|c| c.is_ascii_digit()) {
        return Err(ApiError::new(
            ErrorKind::InvalidRequest,
            "ruc must have 11 digits",
        ));
    }
    let apis = apis(&config, request.environment)?;
    let http = client()?;
    let result = async {
        let token = token(&http, &apis, &request.credentials).await?;
        let url = format!(
            "{}/v1/contribuyente/facturanegociable/comprobantes/{}-{}-{}-{}/conformidad",
            apis.sire,
            request.ruc,
            request.tipo_comprobante,
            request.serie.to_uppercase(),
            request.numero
        );
        let (status, body) = get_json(&http, &token, &url).await?;
        Ok(conformity_of(status, &body))
    }
    .await;
    Ok(Json(match result {
        Ok((conformity, description)) => ConformityResponse {
            state: CallState::Ok,
            conformity,
            description,
        },
        Err(failure) => {
            let failed = TicketResponse::failed(failure);
            ConformityResponse {
                state: failed.state,
                conformity: ConformityState::Unknown,
                description: failed.description,
            }
        }
    }))
}

fn conformity_of(status: u16, body: &serde_json::Value) -> (ConformityState, Option<String>) {
    let description = body["descripcion"].as_str().map(str::to_owned);
    if status == 404 {
        return (ConformityState::NotFound, description);
    }
    if !(200..300).contains(&status) {
        return (
            ConformityState::Unknown,
            Some(format!("SUNAT answered {status}")),
        );
    }
    let estado = match &body["estado"] {
        serde_json::Value::String(s) => s.clone(),
        serde_json::Value::Number(n) => n.to_string(),
        _ => String::new(),
    };
    let state = match estado.as_str() {
        "1" => ConformityState::Pending,
        "2" => ConformityState::Agreed,
        "3" => ConformityState::Objected,
        "4" => ConformityState::SilentAgreement,
        "0" => ConformityState::NotFound,
        _ => ConformityState::Unknown,
    };
    (state, description)
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn ticket_states_are_mapped() {
        let body = json!({"registros": [{"numTicket": "2027001", "codEstadoProceso": "06", "desEstadoProceso": "Terminado",
            "archivoReporte": [{"codTipoAchivoReporte": "01", "nomArchivoReporte": "LE2012345678920270100140400021112.zip"}]}]});
        let (state, _, files) = ticket_state(&body, "2027001");
        assert_eq!(state, TicketState::Done);
        assert_eq!(files.len(), 1);
        assert_eq!(files[0].1, "01");
        let body = json!({"registros": [{"numTicket": "1", "codEstadoProceso": "02", "desEstadoProceso": "En proceso"}]});
        assert_eq!(ticket_state(&body, "1").0, TicketState::Pending);
        let body = json!({"registros": [{"numTicket": "1", "codEstadoProceso": "07", "desEstadoProceso": "Terminado con errores"}]});
        assert_eq!(ticket_state(&body, "1").0, TicketState::Error);
        assert_eq!(ticket_state(&json!({}), "1").0, TicketState::Pending);
    }

    #[test]
    fn conformity_states_are_mapped() {
        assert_eq!(
            conformity_of(200, &json!({"estado": "2"})).0,
            ConformityState::Agreed
        );
        assert_eq!(
            conformity_of(200, &json!({"estado": 3})).0,
            ConformityState::Objected
        );
        assert_eq!(
            conformity_of(200, &json!({"estado": "4"})).0,
            ConformityState::SilentAgreement
        );
        assert_eq!(conformity_of(404, &json!({})).0, ConformityState::NotFound);
        assert_eq!(conformity_of(500, &json!({})).0, ConformityState::Unknown);
        assert_eq!(
            conformity_of(200, &json!({"estado": "9"})).0,
            ConformityState::Unknown
        );
    }

    #[test]
    fn periods_are_checked() {
        assert!(check_period("202701").is_ok());
        assert!(check_period("202713").is_err());
        assert!(check_period("2027-01").is_err());
    }

    #[test]
    fn the_replacement_carries_its_hash() {
        let (content_type, body) = multipart("LE.zip", b"PK", &"a".repeat(64));
        assert!(content_type.starts_with("multipart/form-data; boundary=xhandler-"));
        let text = String::from_utf8_lossy(&body);
        assert!(text.contains("name=\"hash\"\r\n\r\naaaa"));
        assert!(text.contains("filename=\"LE.zip\""));
    }
}
