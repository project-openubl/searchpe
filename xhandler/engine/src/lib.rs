//! xhandler-engine: the Rust sidecar of xhandler-service (DESIGN.md §4).
//!
//! The pieces xhandler-rust doesn't have yet (DESIGN.md §19) live in their
//! own modules so they can move there unchanged: [`codes`] and [`consult`]
//! (R2), [`rules`] (R3), [`cdr`] (R4), [`xsd`] (R5).

pub mod api;
pub mod cdr;
pub mod codes;
pub mod consult;
pub mod inspect;
pub mod rules;
pub mod sunat_fake;
pub mod xmldsig;
pub mod xsd;
