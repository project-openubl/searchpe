//! XMLDSig verification of signed SUNAT documents (DESIGN.md §11.4 step 4,
//! §5): the OSE must not issue a CDR for a document altered after signing,
//! and pre-signed uploads are checked too.
//!
//! SUNAT's profile: one enveloped `ds:Signature` with inclusive C14N 1.0,
//! RSA with SHA-256 (or SHA-1 in older documents), one `Reference URI=""`
//! with the enveloped-signature transform, and the signer's certificate in
//! `ds:KeyInfo/ds:X509Data/ds:X509Certificate`. Checked:
//!
//! 1. the reference digest: the document without its `ds:Signature`,
//!    canonicalized, hashed, equals `ds:DigestValue`;
//! 2. the signature: `ds:SignedInfo` canonicalized (with its in-scope
//!    namespaces), verified against `ds:SignatureValue` with the
//!    certificate's public key.
//!
//! Whether the certificate belongs to the issuer (SUNAT's list, 2325/2326)
//! is a separate check, not done here.

use base64::{engine::general_purpose::STANDARD, Engine as _};
use libxml::parser::Parser;
use libxml::tree::c14n::{CanonicalizationMode, CanonicalizationOptions};
use libxml::tree::{Document, Node};
use sha2::Digest as _;

const DS_NS: &str = "http://www.w3.org/2000/09/xmldsig#";

#[derive(Debug, PartialEq)]
pub enum Verdict {
    Valid,
    /// The document was changed after signing
    DigestMismatch,
    /// The signature doesn't match the certificate (or SignedInfo was changed)
    BadSignature,
    /// No signature, or one this profile doesn't support
    Unsupported(String),
}

const C14N: &str = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315";
const EXC_C14N: &str = "http://www.w3.org/2001/10/xml-exc-c14n#";

/// The canonicalization an algorithm URI names (inclusive or exclusive C14N 1.0, without comments)
fn c14n(algorithm: &str, prefixes: Vec<String>) -> Result<CanonicalizationOptions, Verdict> {
    let mode = match algorithm {
        C14N => CanonicalizationMode::Canonical1_0,
        EXC_C14N => CanonicalizationMode::ExclusiveCanonical1_0,
        other => return Err(Verdict::Unsupported(format!("canonicalization {other}"))),
    };
    Ok(CanonicalizationOptions {
        mode,
        with_comments: false,
        inclusive_ns_prefixes: prefixes,
    })
}

/// The ec:InclusiveNamespaces PrefixList of an exclusive canonicalization, if any
fn prefixes(method: &Node) -> Vec<String> {
    let mut child = method.get_first_element_child();
    while let Some(c) = child {
        if c.get_name() == "InclusiveNamespaces" {
            return attribute(&c, "PrefixList")
                .split_whitespace()
                .map(str::to_owned)
                .collect();
        }
        child = c.get_next_element_sibling();
    }
    vec![]
}

fn find(node: &Node, local_name: &str) -> Option<Node> {
    if node.get_name() == local_name
        && node
            .get_namespace()
            .is_some_and(|ns| ns.get_href() == DS_NS)
    {
        return Some(node.clone());
    }
    let mut child = node.get_first_element_child();
    while let Some(c) = child {
        if let Some(found) = find(&c, local_name) {
            return Some(found);
        }
        child = c.get_next_element_sibling();
    }
    None
}

fn attribute(node: &Node, name: &str) -> String {
    node.get_attribute(name).unwrap_or_default()
}

fn base64(text: &str) -> Result<Vec<u8>, Verdict> {
    let compact: String = text.chars().filter(|c| !c.is_whitespace()).collect();
    STANDARD
        .decode(compact)
        .map_err(|_| Verdict::Unsupported("invalid base64 in the signature".into()))
}

/// Verifies the signature of a document given as the bytes SUNAT receives.
pub fn verify(bytes: &[u8]) -> Verdict {
    let parser = Parser::default();
    let document: Document = match parser.parse_string(bytes) {
        Ok(document) => document,
        Err(_) => return Verdict::Unsupported("unparseable document".into()),
    };
    match check(&document) {
        Ok(verdict) | Err(verdict) => verdict,
    }
}

fn check(document: &Document) -> Result<Verdict, Verdict> {
    let root = document
        .get_root_element()
        .ok_or_else(|| Verdict::Unsupported("no root element".into()))?;
    let mut signature =
        find(&root, "Signature").ok_or_else(|| Verdict::Unsupported("not signed".into()))?;
    let mut signed_info = find(&signature, "SignedInfo")
        .ok_or_else(|| Verdict::Unsupported("no SignedInfo".into()))?;
    let canonicalization = find(&signed_info, "CanonicalizationMethod")
        .ok_or_else(|| Verdict::Unsupported("no CanonicalizationMethod".into()))?;
    let signed_info_options = c14n(
        &attribute(&canonicalization, "Algorithm"),
        prefixes(&canonicalization),
    )?;
    let method = find(&signed_info, "SignatureMethod")
        .map(|n| attribute(&n, "Algorithm"))
        .unwrap_or_default();
    let reference = find(&signed_info, "Reference")
        .ok_or_else(|| Verdict::Unsupported("no Reference".into()))?;
    if !attribute(&reference, "URI").is_empty() {
        return Err(Verdict::Unsupported(
            "only Reference URI=\"\" is supported".into(),
        ));
    }
    // Transforms: enveloped-signature, optionally followed by a canonicalization
    // (inclusive C14N 1.0 when none is given)
    let mut document_options = c14n(C14N, vec![])?;
    if let Some(transforms) = find(&reference, "Transforms") {
        let mut transform = transforms.get_first_element_child();
        while let Some(t) = transform {
            let algorithm = attribute(&t, "Algorithm");
            if algorithm == C14N || algorithm == EXC_C14N {
                document_options = c14n(&algorithm, prefixes(&t))?;
            } else if algorithm != "http://www.w3.org/2000/09/xmldsig#enveloped-signature" {
                return Err(Verdict::Unsupported(format!("transform {algorithm}")));
            }
            transform = t.get_next_element_sibling();
        }
    }
    let digest_method = find(&reference, "DigestMethod")
        .map(|n| attribute(&n, "Algorithm"))
        .unwrap_or_default();
    let digest_value = base64(
        &find(&reference, "DigestValue")
            .map(|n| n.get_content())
            .unwrap_or_default(),
    )?;
    let signature_value = base64(
        &find(&signature, "SignatureValue")
            .map(|n| n.get_content())
            .unwrap_or_default(),
    )?;
    let certificate = base64(
        &find(&signature, "X509Certificate")
            .map(|n| n.get_content())
            .ok_or_else(|| Verdict::Unsupported("no X509Certificate".into()))?,
    )?;
    // SignedInfo as signed: canonicalized in place, with its in-scope namespaces
    let signed_info_c14n = signed_info
        .canonicalize(signed_info_options)
        .map_err(|_| Verdict::Unsupported("SignedInfo can't be canonicalized".into()))?;
    // The enveloped-signature transform: the document without the signature
    signature.unlink();
    let document_c14n = document
        .canonicalize(document_options, None)
        .map_err(|_| Verdict::Unsupported("document can't be canonicalized".into()))?;
    let digest = match digest_method.as_str() {
        "http://www.w3.org/2001/04/xmlenc#sha256" => {
            sha2::Sha256::digest(document_c14n.as_bytes()).to_vec()
        }
        "http://www.w3.org/2000/09/xmldsig#sha1" => {
            sha1::Sha1::digest(document_c14n.as_bytes()).to_vec()
        }
        other => return Err(Verdict::Unsupported(format!("digest {other}"))),
    };
    if digest != digest_value {
        return Ok(Verdict::DigestMismatch);
    }
    let verified = match method.as_str() {
        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256" => {
            rsa_verify_sha256(signed_info_c14n.as_bytes(), &signature_value, &certificate)
        }
        "http://www.w3.org/2000/09/xmldsig#rsa-sha1" => {
            rsa_verify_sha1(signed_info_c14n.as_bytes(), &signature_value, &certificate)
        }
        other => return Err(Verdict::Unsupported(format!("signature method {other}"))),
    };
    Ok(if verified {
        Verdict::Valid
    } else {
        Verdict::BadSignature
    })
}

macro_rules! rsa_verifier {
    ($name:ident, $digest:ty) => {
        fn $name(data: &[u8], signature: &[u8], certificate_der: &[u8]) -> bool {
            use rsa::pkcs8::DecodePublicKey;
            use rsa::signature::Verifier;
            use x509_cert::der::{Decode, Encode};
            let Ok(certificate) = x509_cert::Certificate::from_der(certificate_der) else {
                return false;
            };
            let Ok(public_key_info) = certificate
                .tbs_certificate()
                .subject_public_key_info()
                .to_der()
            else {
                return false;
            };
            let Ok(public_key) = rsa::PublicKey::from_public_key_der(&public_key_info) else {
                return false;
            };
            let Ok(signature) = rsa::pkcs1v15::Signature::try_from(signature) else {
                return false;
            };
            rsa::pkcs1v15::VerifyingKey::<$digest>::new(public_key)
                .verify(data, &signature)
                .is_ok()
        }
    };
}

rsa_verifier!(rsa_verify_sha256, sha2::Sha256);
rsa_verifier!(rsa_verify_sha1, sha1::Sha1);
