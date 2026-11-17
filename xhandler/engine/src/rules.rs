//! First slice of xvalidator (DESIGN.md §19 R3): SUNAT validation rules that
//! need no SUNAT lists, returning SUNAT's return codes. Data-driven: each
//! rule is a row of [`RULES`] (code, message, document types, check), so
//! rules are added without touching the engine. Codes and messages come from
//! SUNAT's `AjustesValidacionesCPE` v2026-02-12 workbook.

use std::sync::LazyLock;

use chrono::NaiveDate;
use regex::Regex;
use rust_decimal_like::parse_amount;
use serde::Serialize;

use crate::inspect::Facts;

#[derive(Serialize, Debug, Clone, PartialEq)]
pub struct Finding {
    pub code: String,
    pub message: String,
}

#[derive(Clone, Copy, PartialEq, Debug)]
pub enum Severity {
    /// SUNAT rejects the document (ERROR in the workbook)
    Error,
    /// SUNAT accepts it with an observation (OBSERV)
    Observation,
}

/// What a rule sees: the document's facts and Lima's date of today
pub struct Context<'a> {
    pub facts: &'a Facts,
    pub today: NaiveDate,
}

pub struct Rule {
    pub code: &'static str,
    pub message: &'static str,
    pub severity: Severity,
    /// Root elements the rule applies to
    pub document_types: &'static [&'static str],
    /// True when the document breaks the rule
    pub violated: fn(&Context) -> bool,
}

const COMPROBANTES: &[&str] = &["Invoice", "CreditNote", "DebitNote"];
const NOTES: &[&str] = &["CreditNote", "DebitNote"];
const ALL: &[&str] = &[
    "Invoice",
    "CreditNote",
    "DebitNote",
    "DespatchAdvice",
    "Perception",
    "Retention",
    "SummaryDocuments",
    "VoidedDocuments",
];

pub static RULES: &[Rule] = &[
    // General sheet: the ZIP name starts with the issuer's RUC, "que inicie con
    // 1 o 2" and has 11 digits. SUNAT has no separate code for the RUC's
    // format: a malformed RUC fails the file name (0151). The check digit
    // isn't checked by SUNAT (its beta RUC 12345678912 doesn't pass it).
    Rule {
        code: "0151",
        message: "El nombre del archivo ZIP es incorrecto: el RUC del emisor debe tener 11 dígitos y empezar con 1 o 2",
        severity: Severity::Error,
        document_types: ALL,
        violated: |c| !RUC.is_match(c.facts.supplier_ruc.as_deref().unwrap_or_default()),
    },
    // Factura2_0/Boleta2_0/NotaCredito2_0/NotaDebito2_0 row 3: cbc:ID format
    Rule {
        code: "1001",
        message: "ID - El dato SERIE-CORRELATIVO no cumple con el formato de acuerdo al tipo de comprobante",
        severity: Severity::Error,
        document_types: COMPROBANTES,
        violated: |c| !serie_numero_ok(c.facts),
    },
    // Row 4: cbc:IssueDate more than two days after the sending date (Lima)
    Rule {
        code: "2329",
        message: "La fecha de emision se encuentra fuera del limite permitido",
        severity: Severity::Error,
        document_types: COMPROBANTES,
        violated: |c| {
            issue_date(c.facts).is_some_and(|date| (date - c.today).num_days() > 2)
        },
    },
    // Boleta over S/ 700 without an identified buyer. SUNAT's code is 2514
    // (Resumen_Diario1_1 row 10, "mayor a 700"); PEN only for now.
    Rule {
        code: "2514",
        message: "No existe información de receptor de documento. Una boleta de más de S/ 700.00 debe identificar al adquirente",
        severity: Severity::Error,
        document_types: &["Invoice"],
        violated: |c| {
            let f = c.facts;
            f.tipo_comprobante().as_deref() == Some("03")
                && f.currency.as_deref().unwrap_or("PEN") == "PEN"
                && f.total.as_deref().and_then(parse_amount).is_some_and(|t| t > 70_000)
                && !buyer_identified(f)
        },
    },
    // NotaCredito2_0/NotaDebito2_0 row 18: the affected document is required,
    // except for credit note type 10 and debit note type 03
    Rule {
        code: "2524",
        message: "Debe indicar el documento afectado por la nota",
        severity: Severity::Error,
        document_types: NOTES,
        violated: |c| {
            let f = c.facts;
            let exempt = matches!(
                (f.document_type.as_str(), f.note_type.as_deref()),
                ("CreditNote", Some("10")) | ("DebitNote", Some("03"))
            );
            f.billing_references == 0 && !exempt
        },
    },
    // A note modifies exactly one document: RS 000048-2026 ("una factura", from
    // 2027-01-01) and the service's own rule. SUNAT's code for "solo es
    // permitido registrar un documento que modifica" is 3194.
    Rule {
        code: "3194",
        message: "La nota solo puede modificar un documento",
        severity: Severity::Error,
        document_types: NOTES,
        violated: |c| c.facts.billing_references > 1,
    },
];

static RUC: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"^[12]\d{10}$").expect("valid regex"));
static FACTURA: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"^(F[A-Z0-9]{3}|\d{1,4})-\d{1,8}$").expect("valid regex"));
static BOLETA: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"^(B[A-Z0-9]{3}|\d{1,4})-\d{1,8}$").expect("valid regex"));
static NOTE: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"^([FB][A-Z0-9]{3}|\d{1,4})-\d{1,8}$").expect("valid regex"));

fn serie_numero_ok(facts: &Facts) -> bool {
    let id = facts.id.as_deref().unwrap_or_default();
    match facts.document_type.as_str() {
        "Invoice" => match facts.tipo_comprobante().as_deref() {
            Some("01") => FACTURA.is_match(id),
            Some("03") => BOLETA.is_match(id),
            _ => FACTURA.is_match(id) || BOLETA.is_match(id),
        },
        _ => NOTE.is_match(id),
    }
}

fn issue_date(facts: &Facts) -> Option<NaiveDate> {
    NaiveDate::parse_from_str(facts.issue_date.as_deref()?, "%Y-%m-%d").ok()
}

/// A buyer is identified with a document type other than "0"/"-" (no
/// document) and a number other than "-" or zeros
fn buyer_identified(facts: &Facts) -> bool {
    let tipo = facts.customer_tipo.as_deref().unwrap_or("-").trim();
    let numero = facts.customer_numero.as_deref().unwrap_or("-").trim();
    !matches!(tipo, "" | "0" | "-") && !numero.chars().all(|c| c == '-' || c == '0')
}

/// Amounts as integer cents, without floating point
mod rust_decimal_like {
    pub fn parse_amount(value: &str) -> Option<i64> {
        let value = value.trim();
        let (units, cents) = value.split_once('.').unwrap_or((value, ""));
        let units: i64 = units.parse().ok()?;
        let mut cents = cents.chars().take(2).collect::<String>();
        while cents.len() < 2 {
            cents.push('0');
        }
        let cents: i64 = cents.parse().ok()?;
        Some(units * 100 + if units < 0 { -cents } else { cents })
    }
}

/// Runs every rule that applies to the document type
pub fn check(facts: &Facts, today: NaiveDate) -> (Vec<Finding>, Vec<Finding>) {
    let context = Context { facts, today };
    let mut errors = Vec::new();
    let mut observations = Vec::new();
    for rule in RULES
        .iter()
        .filter(|rule| rule.document_types.contains(&facts.document_type.as_str()))
    {
        if (rule.violated)(&context) {
            let finding = Finding {
                code: rule.code.to_string(),
                message: rule.message.to_string(),
            };
            match rule.severity {
                Severity::Error => errors.push(finding),
                Severity::Observation => observations.push(finding),
            }
        }
    }
    (errors, observations)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn today() -> NaiveDate {
        NaiveDate::from_ymd_opt(2026, 11, 16).unwrap()
    }

    fn factura() -> Facts {
        Facts {
            document_type: "Invoice".into(),
            id: Some("F001-1".into()),
            issue_date: Some("2026-11-16".into()),
            type_code: Some("01".into()),
            currency: Some("PEN".into()),
            total: Some("118.00".into()),
            supplier_ruc: Some("12345678912".into()),
            customer_tipo: Some("6".into()),
            customer_numero: Some("12121212121".into()),
            ..Default::default()
        }
    }

    fn boleta() -> Facts {
        Facts {
            id: Some("B001-1".into()),
            type_code: Some("03".into()),
            customer_tipo: Some("1".into()),
            customer_numero: Some("12345678".into()),
            ..factura()
        }
    }

    fn credit_note() -> Facts {
        Facts {
            document_type: "CreditNote".into(),
            id: Some("FC01-1".into()),
            type_code: None,
            billing_references: 1,
            note_type: Some("01".into()),
            ..factura()
        }
    }

    fn codes(facts: &Facts) -> Vec<String> {
        check(facts, today())
            .0
            .into_iter()
            .map(|f| f.code)
            .collect()
    }

    #[test]
    fn valid_documents_pass() {
        assert!(codes(&factura()).is_empty());
        assert!(codes(&boleta()).is_empty());
        assert!(codes(&credit_note()).is_empty());
    }

    #[test]
    fn issuer_ruc_format() {
        for ruc in [
            "2012345678",
            "201234567890",
            "30123456789",
            "2012345678X",
            "",
        ] {
            let facts = Facts {
                supplier_ruc: Some(ruc.into()),
                ..factura()
            };
            assert_eq!(codes(&facts), ["0151"], "ruc {ruc}");
        }
        let summary = Facts {
            document_type: "SummaryDocuments".into(),
            id: Some("RC-20261116-1".into()),
            supplier_ruc: Some("123".into()),
            ..Default::default()
        };
        assert_eq!(codes(&summary), ["0151"]);
    }

    #[test]
    fn serie_format_per_document_type() {
        let with_id = |facts: Facts, id: &str| Facts {
            id: Some(id.into()),
            ..facts
        };
        assert_eq!(codes(&with_id(factura(), "B001-1")), ["1001"]);
        assert_eq!(codes(&with_id(factura(), "F01-1")), ["1001"]);
        assert_eq!(codes(&with_id(factura(), "F001-123456789")), ["1001"]);
        assert!(codes(&with_id(factura(), "0001-12345678")).is_empty());
        assert_eq!(codes(&with_id(boleta(), "F001-1")), ["1001"]);
        assert!(codes(&with_id(credit_note(), "BC01-1")).is_empty());
        assert_eq!(codes(&with_id(credit_note(), "X001-1")), ["1001"]);
    }

    #[test]
    fn issue_date_at_most_two_days_ahead() {
        let on = |date: &str| Facts {
            issue_date: Some(date.into()),
            ..factura()
        };
        assert!(codes(&on("2026-11-18")).is_empty());
        assert_eq!(codes(&on("2026-11-19")), ["2329"]);
        assert!(codes(&on("2026-01-01")).is_empty());
    }

    #[test]
    fn boleta_over_700_needs_a_buyer() {
        let anonymous = |total: &str| Facts {
            total: Some(total.into()),
            customer_tipo: Some("0".into()),
            customer_numero: Some("00000000".into()),
            ..boleta()
        };
        assert!(codes(&anonymous("700.00")).is_empty());
        assert_eq!(codes(&anonymous("700.01")), ["2514"]);
        let identified = Facts {
            total: Some("1000".into()),
            ..boleta()
        };
        assert!(codes(&identified).is_empty());
        let dollars = Facts {
            currency: Some("USD".into()),
            ..anonymous("1000")
        };
        assert!(codes(&dollars).is_empty());
        let no_customer = Facts {
            customer_tipo: None,
            customer_numero: None,
            ..anonymous("701")
        };
        assert_eq!(codes(&no_customer), ["2514"]);
    }

    #[test]
    fn notes_reference_exactly_one_document() {
        let with = |references: usize| Facts {
            billing_references: references,
            ..credit_note()
        };
        assert_eq!(codes(&with(0)), ["2524"]);
        assert_eq!(codes(&with(2)), ["3194"]);
        let otros = Facts {
            note_type: Some("10".into()),
            ..with(0)
        };
        assert!(codes(&otros).is_empty());
        let debit = Facts {
            document_type: "DebitNote".into(),
            ..with(0)
        };
        assert_eq!(codes(&debit), ["2524"]);
    }

    #[test]
    fn amounts() {
        assert_eq!(parse_amount("700"), Some(70000));
        assert_eq!(parse_amount("700.5"), Some(70050));
        assert_eq!(parse_amount("700.019"), Some(70001));
        assert_eq!(parse_amount("x"), None);
    }
}
