//! Classification of SUNAT codes 99–1999 ("not processed"), DESIGN.md §9.2.3.
//!
//! Data from the *CódigosRetorno* sheet of SUNAT's `AjustesValidacionesCPE`
//! workbook (v2026-02-12), grouped by what the service does with them. Kept in
//! its own module so it can move to xsender (R2) as is.

use serde::Serialize;

/// What a code in 99–1999 means for the next attempt (§9.2.3)
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum CodeClass {
    /// SUNAT's system failed: retry with backoff
    TransientSystem,
    /// SUNAT could not produce or return the receipt: resolve, then retry
    TransientConstancia,
    /// An equal document is being processed: wait, then resolve
    TransientInProcess,
    /// 1033, registered before "con otros datos": digest comparison
    AlreadyRegistered,
    /// 1032, already reported, voided or rejected: resolve; definitive
    AlreadyFinal,
    /// 1079, boleta only through a resumen diario
    RouteChange,
    /// The ticket is not ours or doesn't exist
    TicketLost,
    PermanentCredentials,
    PermanentPackage,
    PermanentContent,
    PermanentReferences,
    /// Homologation, utility channel, contingency
    NotApplicable,
    /// In 99–1999 but not in the table: resolve first, then alert
    Unclassified,
}

/// The class of a SUNAT code, or `None` outside 99–1999 (0, 98, rejections
/// 2000–3999 and observations ≥ 4000 are not "not processed" codes).
///
/// Arms are matched in order: specific codes come before the ranges that
/// contain them (154 before 151–161; 1032, 1033, 1039, 1040 before 1001–1077).
pub fn classify(code: u32) -> Option<CodeClass> {
    use CodeClass::*;
    Some(match code {
        100 | 109 | 130..=138 | 200 | 201 | 253 | 254 => TransientSystem,
        125 | 307 => TransientConstancia,
        140 => TransientInProcess,
        1033 => AlreadyRegistered,
        1032 => AlreadyFinal,
        1079 => RouteChange,
        126 | 127 => TicketLost,
        101..=106 | 110..=113 | 154 | 1078 | 1080 | 1085 | 1086 => PermanentCredentials,
        403 | 404 | 1039 | 1040 => PermanentReferences,
        400..=402 | 1081 | 1082 | 1084 => NotApplicable,
        151..=161 | 202..=204 | 250..=252 | 300..=306 => PermanentPackage,
        1001..=1077 | 1083 => PermanentContent,
        99..=1999 => Unclassified,
        _ => return None,
    })
}

#[cfg(test)]
mod tests {
    use super::CodeClass::*;
    use super::*;

    fn all(codes: &[u32], class: CodeClass) {
        for &code in codes {
            assert_eq!(classify(code), Some(class), "code {code}");
        }
    }

    #[test]
    fn outside_99_to_1999_has_no_class() {
        for code in [0, 1, 97, 98, 2000, 2335, 3999, 4000, 4252, 99999] {
            assert_eq!(classify(code), None, "code {code}");
        }
    }

    #[test]
    fn transient() {
        all(
            &[100, 109, 130, 131, 137, 138, 200, 201, 253, 254],
            TransientSystem,
        );
        all(&[125, 307], TransientConstancia);
        all(&[140], TransientInProcess);
    }

    #[test]
    fn special() {
        all(&[1033], AlreadyRegistered);
        all(&[1032], AlreadyFinal);
        all(&[1079], RouteChange);
        all(&[126, 127], TicketLost);
    }

    #[test]
    fn permanent() {
        all(
            &[
                101, 102, 105, 106, 110, 111, 112, 113, 154, 1078, 1080, 1085, 1086,
            ],
            PermanentCredentials,
        );
        all(
            &[
                151, 152, 153, 155, 160, 161, 202, 203, 204, 250, 251, 252, 300, 305, 306,
            ],
            PermanentPackage,
        );
        all(
            &[1001, 1002, 1031, 1034, 1038, 1041, 1076, 1077, 1083],
            PermanentContent,
        );
        all(&[403, 404, 1039, 1040], PermanentReferences);
        all(&[400, 401, 402, 1081, 1082, 1084], NotApplicable);
    }

    #[test]
    fn codes_next_to_the_ranges_are_unclassified() {
        all(
            &[
                99, 107, 108, 114, 129, 139, 141, 150, 162, 199, 205, 249, 255, 299, 308, 399, 405,
                1000, 1087, 1999,
            ],
            Unclassified,
        );
    }

    #[test]
    fn serializes_as_snake_case() {
        assert_eq!(
            serde_json::to_value(PermanentCredentials).unwrap(),
            "permanent_credentials"
        );
    }
}
