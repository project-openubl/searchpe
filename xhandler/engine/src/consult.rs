//! Answers of SUNAT's consult services (DESIGN.md §9.2.2, R2), mapped
//! explicitly: their codes are their own ("0001" accepted, "0011" doesn't
//! exist...), never read through the 99–1999 ranges of the send codes.
//! Kept in its own module so it can move to xsender.
//!
//! xsender's `ConsultResponse` gives either `Status { status_code,
//! status_message, cdr_base64 }` (the `<status>`/`<statusCdr>` element) or a
//! SOAP `Fault`. The mappings:
//!
//! billConsultService `getStatusCdr` / `getStatus` ([`status_cdr`]):
//!
//! | statusCode | Meaning (SUNAT)                                  | status      |
//! |------------|--------------------------------------------------|-------------|
//! | 0001       | El comprobante existe y está aceptado            | accepted    |
//! | 0002       | El comprobante existe pero está rechazado        | rejected    |
//! | 0003       | El comprobante existe pero está de baja          | voided      |
//! | 0004 + CDR | La constancia existe (getStatusCdr)              | from the CDR's ResponseCode: 0/≥4000 accepted, 2000–3999 rejected, else unknown |
//! | 0011       | El comprobante de pago electrónico no existe     | not_found   |
//! | 0012       | El comprobante no le pertenece (not ours to see) | not_found   |
//! | message containing "no existe"                                | not_found   |
//! | anything else (format errors 0004–0010 without CDR, ...)      | unknown     |
//! | SOAP fault                                                    | unknown, with the fault's code |
//!
//! billValidService `validaCDPcriterios` ([`validity`]) answers statusCode "0"
//! when the query ran, and the document's state in statusMessage ("El
//! comprobante existe y está aceptado.", "... no existe", "... de baja",
//! "... rechazado"), so the message decides: "no existe"/"no informado" →
//! not_found, "baja"/"anulad" → voided, "rechaz"/"no autorizado" → rejected,
//! "acept"/"autorizado"/"válido" → valid, else unknown.
//!
//! billValidService `verificaCPEarchivo` ([`validity_file`]): "no existe"/"no
//! se encuentra"/"no informado" → not_found; "no corresponde"/"no coincide"/
//! "modificad"/"alterad"/"difiere" → differs; statusCode "0" or
//! "corresponde"/"coincide"/"válido" → matches; else unknown.

use serde::Serialize;
use xhandler::prelude::{CdrMetadata, ConsultResponse, Status};

#[derive(Serialize, Debug, PartialEq, Clone, Copy)]
#[serde(rename_all = "snake_case")]
pub enum CdrStatus {
    Accepted,
    Rejected,
    Voided,
    NotFound,
    /// SUNAT answered something else, or may not have answered at all
    Unknown,
    /// The request never reached SUNAT
    NotDelivered,
}

#[derive(Serialize, Debug, PartialEq)]
pub struct StatusCdrResponse {
    pub status: CdrStatus,
    /// SUNAT's consult status code, e.g. "0001", or the fault code
    pub code: Option<String>,
    pub description: Option<String>,
    pub cdr_zip: Option<String>,
}

#[derive(Serialize, Debug, PartialEq, Clone, Copy)]
#[serde(rename_all = "snake_case")]
pub enum ValidityState {
    Valid,
    NotFound,
    Voided,
    Rejected,
    Unknown,
    NotDelivered,
}

#[derive(Serialize, Debug, PartialEq, Clone, Copy)]
#[serde(rename_all = "snake_case")]
pub enum FileState {
    /// SUNAT holds exactly these bytes
    Matches,
    /// SUNAT holds another document with this number
    Differs,
    NotFound,
    Unknown,
    NotDelivered,
}

#[derive(Serialize, Debug, PartialEq)]
pub struct ValidityResponse<S> {
    pub state: S,
    pub code: Option<String>,
    pub description: Option<String>,
}

/// Lowercase, without Spanish accents, for keyword matching
fn normalized(message: &str) -> String {
    message
        .to_lowercase()
        .chars()
        .map(|c| match c {
            'á' => 'a',
            'é' => 'e',
            'í' => 'i',
            'ó' => 'o',
            'ú' => 'u',
            c => c,
        })
        .collect()
}

fn fault_code(fault: &xhandler::prelude::SunatFault) -> String {
    fault
        .sunat_code()
        .map(|code| format!("{code:04}"))
        .unwrap_or_else(|| fault.code.clone())
}

pub fn status_cdr(response: ConsultResponse) -> StatusCdrResponse {
    let status = match response {
        ConsultResponse::Fault(fault) => {
            return StatusCdrResponse {
                status: CdrStatus::Unknown,
                code: Some(fault_code(&fault)),
                description: Some(fault.message),
                cdr_zip: None,
            }
        }
        ConsultResponse::Status(status) => status,
    };
    let code = status.status_code.trim().to_string();
    let message = normalized(status.status_message.as_deref().unwrap_or_default());
    let mapped = match (code.as_str(), &status.cdr_base64) {
        ("0001", _) => CdrStatus::Accepted,
        ("0002", _) => CdrStatus::Rejected,
        ("0003", _) => CdrStatus::Voided,
        ("0011" | "0012", _) => CdrStatus::NotFound,
        (_, Some(cdr)) => match CdrMetadata::from_zip_base64(cdr).map(|cdr| cdr.status()) {
            Ok(Status::Accepted) => CdrStatus::Accepted,
            Ok(Status::Rejected) => CdrStatus::Rejected,
            _ => CdrStatus::Unknown,
        },
        _ if message.contains("no existe") => CdrStatus::NotFound,
        _ => CdrStatus::Unknown,
    };
    StatusCdrResponse {
        status: mapped,
        code: Some(code),
        description: status.status_message,
        cdr_zip: status.cdr_base64,
    }
}

pub fn validity(response: ConsultResponse) -> ValidityResponse<ValidityState> {
    let status = match response {
        ConsultResponse::Fault(fault) => {
            return ValidityResponse {
                state: ValidityState::Unknown,
                code: Some(fault_code(&fault)),
                description: Some(fault.message),
            }
        }
        ConsultResponse::Status(status) => status,
    };
    let message = normalized(status.status_message.as_deref().unwrap_or_default());
    let has = |words: &[&str]| words.iter().any(|word| message.contains(word));
    let state = if has(&["no existe", "no informado", "no ha sido informado"]) {
        ValidityState::NotFound
    } else if has(&["baja", "anulad"]) {
        ValidityState::Voided
    } else if has(&["rechaz", "no autorizado"]) {
        ValidityState::Rejected
    } else if has(&["acept", "autorizado", "valido"]) {
        ValidityState::Valid
    } else {
        ValidityState::Unknown
    };
    ValidityResponse {
        state,
        code: Some(status.status_code.trim().to_string()),
        description: status.status_message,
    }
}

pub fn validity_file(response: ConsultResponse) -> ValidityResponse<FileState> {
    let status = match response {
        ConsultResponse::Fault(fault) => {
            return ValidityResponse {
                state: FileState::Unknown,
                code: Some(fault_code(&fault)),
                description: Some(fault.message),
            }
        }
        ConsultResponse::Status(status) => status,
    };
    let code = status.status_code.trim().to_string();
    let message = normalized(status.status_message.as_deref().unwrap_or_default());
    let has = |words: &[&str]| words.iter().any(|word| message.contains(word));
    let state = if has(&[
        "no existe",
        "no se encuentra",
        "no informado",
        "no ha sido informado",
    ]) {
        FileState::NotFound
    } else if has(&[
        "no corresponde",
        "no coincide",
        "modificad",
        "alterad",
        "difiere",
    ]) {
        FileState::Differs
    } else if code == "0" || has(&["corresponde", "coincide", "valido"]) {
        FileState::Matches
    } else {
        FileState::Unknown
    };
    ValidityResponse {
        state,
        code: Some(code),
        description: status.status_message,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use xhandler::prelude::{ConsultStatus, SunatFault};

    fn answer(code: &str, message: &str, cdr: Option<&str>) -> ConsultResponse {
        ConsultResponse::Status(ConsultStatus {
            status_code: code.into(),
            status_message: Some(message.into()),
            cdr_base64: cdr.map(String::from),
        })
    }

    #[test]
    fn status_cdr_codes() {
        let status = |code: &str, message: &str| status_cdr(answer(code, message, None)).status;
        assert_eq!(
            status("0001", "El comprobante existe y está aceptado"),
            CdrStatus::Accepted
        );
        assert_eq!(
            status("0002", "El comprobante existe pero está rechazado"),
            CdrStatus::Rejected
        );
        assert_eq!(
            status("0003", "El comprobante existe pero está de baja"),
            CdrStatus::Voided
        );
        assert_eq!(
            status("0011", "El comprobante de pago electrónico no existe"),
            CdrStatus::NotFound
        );
        assert_eq!(status("0012", "no le pertenece"), CdrStatus::NotFound);
        assert_eq!(
            status("0127", "El comprobante no existe"),
            CdrStatus::NotFound
        );
        assert_eq!(
            status("0005", "Formato del tipo de comprobante no es válido"),
            CdrStatus::Unknown
        );
        // A CDR that can't be read
        assert_eq!(
            status_cdr(answer("0004", "La constancia existe", Some("UEsDBA=="))).status,
            CdrStatus::Unknown
        );
    }

    #[test]
    fn status_cdr_fault() {
        let response = status_cdr(ConsultResponse::Fault(SunatFault {
            code: "soap-env:Client.0109".into(),
            message: "El sistema no puede responder".into(),
            http_status: Some(500),
        }));
        assert_eq!(response.status, CdrStatus::Unknown);
        assert_eq!(response.code.as_deref(), Some("0109"));
    }

    #[test]
    fn validity_messages() {
        let state = |message: &str| validity(answer("0", message, None)).state;
        assert_eq!(
            state("El comprobante existe y está aceptado."),
            ValidityState::Valid
        );
        assert_eq!(state("El comprobante no existe."), ValidityState::NotFound);
        assert_eq!(
            state("El comprobante existe pero ha sido comunicado de baja"),
            ValidityState::Voided
        );
        assert_eq!(
            state("El comprobante existe pero está rechazado"),
            ValidityState::Rejected
        );
        assert_eq!(state("???"), ValidityState::Unknown);
    }

    #[test]
    fn validity_file_messages() {
        let state = |code: &str, message: &str| validity_file(answer(code, message, None)).state;
        assert_eq!(
            state("0", "El comprobante es válido y corresponde al registrado"),
            FileState::Matches
        );
        assert_eq!(
            state("1", "El comprobante no corresponde al registrado en SUNAT"),
            FileState::Differs
        );
        assert_eq!(
            state("2", "El comprobante no existe en SUNAT"),
            FileState::NotFound
        );
        assert_eq!(state("9", "???"), FileState::Unknown);
    }
}
