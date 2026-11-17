//! XSD validation with libxml2 (DESIGN.md §19 R5). Kept apart from the rules
//! (`rules.rs`) so it can become the `xxsd` crate.
//!
//! - The schemas are embedded in the binary (`resources/xsd`). libxml2 reads
//!   them through input callbacks registered for a virtual directory
//!   ([`EMBEDDED_DIR`]), so `xs:import`/`xs:include` resolve relative to the
//!   importing schema, among the embedded files only. Every other external
//!   entity is refused by a global entity loader: no disk, no network.
//! - Each schema is compiled once per process, the first time the pool is
//!   used ([`init`] does it at startup). Compiled schemas are read-only and
//!   shared by all threads.
//! - libxml2 validation contexts aren't thread-safe, so validation runs on a
//!   fixed pool of threads (`XHANDLER_VALIDATE_THREADS`, default: cores),
//!   each with its own context per schema.
//! - Documents are parsed without network access, DTD loading or entity
//!   substitution, and with a size cap.
//!
//! The pool and the compiled schemas are process-wide but immutable after
//! start: requests share no mutable state.

use std::ffi::{c_char, c_int, c_void, CStr, CString};
use std::io::{Cursor, Read};
use std::sync::mpsc::{sync_channel, Receiver, SyncSender};
use std::sync::{Arc, Mutex, OnceLock};

use libxml::bindings;
use libxml::parser::{Parser, ParserOptions};

/// Virtual directory where libxml2 sees the embedded schemas
const EMBEDDED_DIR: &str = "/xhandler-embedded-xsd/";

/// Bigger documents are refused before parsing
pub const MAX_XML_BYTES: usize = 10 * 1024 * 1024;

macro_rules! embedded {
    ($($path:literal),* $(,)?) => {
        &[$(($path, include_bytes!(concat!("../resources/xsd/", $path)) as &[u8])),*]
    };
}

static EMBEDDED: &[(&str, &[u8])] = embedded![
    "2.0/common/CCTS_CCT_SchemaModule-2.0.xsd",
    "2.0/common/CodeList_CurrencyCode_ISO_7_04.xsd",
    "2.0/common/CodeList_LanguageCode_ISO_7_04.xsd",
    "2.0/common/CodeList_MIMEMediaTypeCode_IANA_7_04.xsd",
    "2.0/common/CodeList_UnitCode_UNECE_7_04.xsd",
    "2.0/common/UBL-CommonAggregateComponents-2.0.xsd",
    "2.0/common/UBL-CommonBasicComponents-2.0.xsd",
    "2.0/common/UBL-CommonExtensionComponents-2.0.xsd",
    "2.0/common/UBL-ExtensionContentDatatype-2.0.xsd",
    "2.0/common/UBLPE-SunatAggregateComponents-1.0.xsd",
    "2.0/common/UBL-QualifiedDatatypes-2.0.xsd",
    "2.0/common/UnqualifiedDataTypeSchemaModule-2.0.xsd",
    "2.0/maindoc/UBLPE-Perception-1.0.xsd",
    "2.0/maindoc/UBLPE-Retention-1.0.xsd",
    "2.0/maindoc/UBLPE-SummaryDocuments-1.0.xsd",
    "2.0/maindoc/UBLPE-VoidedDocuments-1.0.xsd",
    "2.1/common/CCTS_CCT_SchemaModule-2.1.xsd",
    "2.1/common/UBL-CommonAggregateComponents-2.1.xsd",
    "2.1/common/UBL-CommonBasicComponents-2.1.xsd",
    "2.1/common/UBL-CommonExtensionComponents-2.1.xsd",
    "2.1/common/UBL-CommonSignatureComponents-2.1.xsd",
    "2.1/common/UBL-ExtensionContentDataType-2.1.xsd",
    "2.1/common/UBL-QualifiedDataTypes-2.1.xsd",
    "2.1/common/UBL-SignatureAggregateComponents-2.1.xsd",
    "2.1/common/UBL-SignatureBasicComponents-2.1.xsd",
    "2.1/common/UBL-UnqualifiedDataTypes-2.1.xsd",
    "2.1/common/UBL-XAdESv132-2.1.xsd",
    "2.1/common/UBL-XAdESv141-2.1.xsd",
    "2.1/common/UBL-xmldsig-core-schema-2.1.xsd",
    "2.1/maindoc/UBL-ApplicationResponse-2.1.xsd",
    "2.1/maindoc/UBL-CreditNote-2.1.xsd",
    "2.1/maindoc/UBL-DebitNote-2.1.xsd",
    "2.1/maindoc/UBL-DespatchAdvice-2.1.xsd",
    "2.1/maindoc/UBL-Invoice-2.1.xsd",
];

/// Main schema of each document type (root element)
const MAIN_SCHEMAS: &[(&str, &str)] = &[
    ("Invoice", "2.1/maindoc/UBL-Invoice-2.1.xsd"),
    ("CreditNote", "2.1/maindoc/UBL-CreditNote-2.1.xsd"),
    ("DebitNote", "2.1/maindoc/UBL-DebitNote-2.1.xsd"),
    ("DespatchAdvice", "2.1/maindoc/UBL-DespatchAdvice-2.1.xsd"),
    (
        "ApplicationResponse",
        "2.1/maindoc/UBL-ApplicationResponse-2.1.xsd",
    ),
    (
        "SummaryDocuments",
        "2.0/maindoc/UBLPE-SummaryDocuments-1.0.xsd",
    ),
    (
        "VoidedDocuments",
        "2.0/maindoc/UBLPE-VoidedDocuments-1.0.xsd",
    ),
    ("Perception", "2.0/maindoc/UBLPE-Perception-1.0.xsd"),
    ("Retention", "2.0/maindoc/UBLPE-Retention-1.0.xsd"),
];

#[derive(Debug, PartialEq)]
pub enum XsdOutcome {
    Valid,
    /// Schema violations, one message per libxml2 diagnostic
    Invalid(Vec<String>),
    /// libxml2 could not parse the document
    Unparseable,
    /// No schema for this document type
    NoSchema,
}

/// Validates the document against the schema of its root element
/// (`document_type`), on the validation pool.
pub async fn validate(document_type: &str, xml: Vec<u8>) -> Result<XsdOutcome, String> {
    let Some(index) = MAIN_SCHEMAS
        .iter()
        .position(|(name, _)| *name == document_type)
    else {
        return Ok(XsdOutcome::NoSchema);
    };
    if xml.len() > MAX_XML_BYTES {
        return Ok(XsdOutcome::Unparseable);
    }
    let (reply, answer) = tokio::sync::oneshot::channel();
    pool()
        .jobs
        .send(Job { index, xml, reply })
        .map_err(|_| "the validation pool stopped".to_string())?;
    answer
        .await
        .map_err(|_| "a validation thread stopped".to_string())
}

/// Compiles the schemas and starts the pool. Called at startup so the first
/// request doesn't pay for it; panics if an embedded schema doesn't compile.
pub fn init() {
    pool();
}

struct Job {
    index: usize,
    xml: Vec<u8>,
    reply: tokio::sync::oneshot::Sender<XsdOutcome>,
}

struct Pool {
    jobs: SyncSender<Job>,
}

/// A compiled schema. libxml2 only reads it during validation, so several
/// threads can validate against it at once, each with its own context.
struct Schema(bindings::xmlSchemaPtr);
// SAFETY: immutable after compilation, never freed (lives for the process)
unsafe impl Send for Schema {}
unsafe impl Sync for Schema {}

fn pool() -> &'static Pool {
    static POOL: OnceLock<Pool> = OnceLock::new();
    POOL.get_or_init(|| {
        let schemas: Arc<Vec<Schema>> = Arc::new(compile_schemas());
        let threads = std::env::var("XHANDLER_VALIDATE_THREADS")
            .ok()
            .and_then(|value| value.parse::<usize>().ok())
            .filter(|threads| *threads > 0)
            .unwrap_or_else(|| {
                std::thread::available_parallelism()
                    .map(usize::from)
                    .unwrap_or(1)
            });
        let (jobs, receiver) = sync_channel::<Job>(threads * 64);
        let receiver = Arc::new(Mutex::new(receiver));
        for n in 0..threads {
            let schemas = schemas.clone();
            let receiver = receiver.clone();
            std::thread::Builder::new()
                .name(format!("xsd-{n}"))
                .spawn(move || worker(&schemas, &receiver))
                .expect("can start a validation thread");
        }
        tracing::info!(
            threads,
            schemas = MAIN_SCHEMAS.len(),
            "XSD validation ready"
        );
        Pool { jobs }
    })
}

fn worker(schemas: &[Schema], receiver: &Mutex<Receiver<Job>>) {
    let mut contexts: Vec<ValidationContext> = schemas.iter().map(ValidationContext::new).collect();
    loop {
        let job = match receiver.lock() {
            Ok(receiver) => match receiver.recv() {
                Ok(job) => job,
                Err(_) => return,
            },
            Err(_) => return,
        };
        let outcome = contexts[job.index].validate(&job.xml);
        let _ = job.reply.send(outcome);
    }
}

// --- compilation ----------------------------------------------------------

fn compile_schemas() -> Vec<Schema> {
    libxml::init_parser();
    // SAFETY: libxml2 global setup, done once before any schema is used
    unsafe {
        bindings::xmlSchemaInitTypes();
        bindings::xmlRegisterInputCallbacks(
            Some(embedded_match),
            Some(embedded_open),
            Some(embedded_read),
            Some(embedded_close),
        );
        DEFAULT_ENTITY_LOADER.get_or_init(|| LoaderFn(bindings::xmlGetExternalEntityLoader()));
        bindings::xmlSetExternalEntityLoader(Some(embedded_only_loader));
    }
    MAIN_SCHEMAS.iter().map(|(_, path)| compile(path)).collect()
}

fn compile(path: &str) -> Schema {
    let url = CString::new(format!("{EMBEDDED_DIR}{path}")).expect("no NUL in paths");
    let mut errors: Box<Vec<String>> = Box::default();
    // SAFETY: the parser context and the error vector outlive the parse
    unsafe {
        let parser = bindings::xmlSchemaNewParserCtxt(url.as_ptr());
        assert!(!parser.is_null(), "schema parser context for {path}");
        bindings::xmlSchemaSetParserStructuredErrors(
            parser,
            error_handler(),
            &mut *errors as *mut Vec<String> as *mut c_void,
        );
        let schema = bindings::xmlSchemaParse(parser);
        bindings::xmlSchemaFreeParserCtxt(parser);
        assert!(
            !schema.is_null(),
            "embedded schema {path} doesn't compile: {errors:?}"
        );
        Schema(schema)
    }
}

/// Wrapper so the default loader can live in a static
struct LoaderFn(bindings::xmlExternalEntityLoader);
// SAFETY: a plain C function pointer
unsafe impl Send for LoaderFn {}
unsafe impl Sync for LoaderFn {}

static DEFAULT_ENTITY_LOADER: OnceLock<LoaderFn> = OnceLock::new();

/// Loads embedded schemas (through libxml2's default loader, which reads
/// them with the input callbacks below) and refuses everything else.
unsafe extern "C" fn embedded_only_loader(
    url: *const c_char,
    id: *const c_char,
    context: bindings::xmlParserCtxtPtr,
) -> bindings::xmlParserInputPtr {
    if url.is_null() || embedded_file(CStr::from_ptr(url).to_string_lossy().as_ref()).is_none() {
        return std::ptr::null_mut();
    }
    match DEFAULT_ENTITY_LOADER.get().and_then(|loader| loader.0) {
        Some(default) => default(url, id, context),
        None => std::ptr::null_mut(),
    }
}

/// The embedded file for a URL inside [`EMBEDDED_DIR`], resolving `.` and `..`
fn embedded_file(url: &str) -> Option<&'static [u8]> {
    let start = url.find(EMBEDDED_DIR)? + EMBEDDED_DIR.len();
    let mut segments: Vec<&str> = Vec::new();
    for segment in url[start..].split('/') {
        match segment {
            "" | "." => {}
            ".." => {
                segments.pop()?;
            }
            segment => segments.push(segment),
        }
    }
    let path = segments.join("/");
    EMBEDDED
        .iter()
        .find(|(name, _)| *name == path)
        .map(|(_, bytes)| *bytes)
}

unsafe extern "C" fn embedded_match(filename: *const c_char) -> c_int {
    if filename.is_null() {
        return 0;
    }
    let name = CStr::from_ptr(filename).to_string_lossy();
    c_int::from(embedded_file(&name).is_some())
}

unsafe extern "C" fn embedded_open(filename: *const c_char) -> *mut c_void {
    let name = CStr::from_ptr(filename).to_string_lossy();
    match embedded_file(&name) {
        Some(bytes) => Box::into_raw(Box::new(Cursor::new(bytes))) as *mut c_void,
        None => std::ptr::null_mut(),
    }
}

unsafe extern "C" fn embedded_read(context: *mut c_void, buffer: *mut c_char, len: c_int) -> c_int {
    let cursor = &mut *(context as *mut Cursor<&'static [u8]>);
    let buffer = std::slice::from_raw_parts_mut(buffer as *mut u8, len.max(0) as usize);
    match cursor.read(buffer) {
        Ok(read) => read as c_int,
        Err(_) => -1,
    }
}

unsafe extern "C" fn embedded_close(context: *mut c_void) -> c_int {
    drop(Box::from_raw(context as *mut Cursor<&'static [u8]>));
    0
}

// --- validation -----------------------------------------------------------

/// A libxml2 validation context for one schema, owned by one thread. The
/// errors are boxed: libxml2 keeps a pointer to them, so they must not move.
#[allow(clippy::box_collection)]
struct ValidationContext {
    context: bindings::xmlSchemaValidCtxtPtr,
    errors: Box<Vec<String>>,
}

impl ValidationContext {
    fn new(schema: &Schema) -> Self {
        let mut errors: Box<Vec<String>> = Box::default();
        // SAFETY: the schema lives for the process; `errors` is boxed, so its
        // address is stable for as long as the context exists
        let context = unsafe {
            let context = bindings::xmlSchemaNewValidCtxt(schema.0);
            assert!(!context.is_null(), "schema validation context");
            bindings::xmlSchemaSetValidStructuredErrors(
                context,
                error_handler(),
                &mut *errors as *mut Vec<String> as *mut c_void,
            );
            context
        };
        Self { context, errors }
    }

    fn validate(&mut self, xml: &[u8]) -> XsdOutcome {
        let options = ParserOptions {
            recover: false,
            no_net: true,
            no_error: true,
            no_warning: true,
            ..Default::default()
        };
        let Ok(document) = Parser::default().parse_string_with_options(xml, options) else {
            return XsdOutcome::Unparseable;
        };
        self.errors.clear();
        // SAFETY: the document and the context are alive and used by this
        // thread only
        let rc = unsafe { bindings::xmlSchemaValidateDoc(self.context, document.doc_ptr()) };
        match rc {
            0 => XsdOutcome::Valid,
            _ => {
                let mut errors = std::mem::take(&mut *self.errors);
                if errors.is_empty() {
                    errors.push(format!("validación XSD fallida (código {rc})"));
                }
                errors.truncate(50);
                XsdOutcome::Invalid(errors)
            }
        }
    }
}

impl Drop for ValidationContext {
    fn drop(&mut self) {
        // SAFETY: created by xmlSchemaNewValidCtxt, freed once
        unsafe { bindings::xmlSchemaFreeValidCtxt(self.context) }
    }
}

/// Collects libxml2 diagnostics into the `Vec<String>` passed as context
unsafe extern "C" fn collect_error(context: *mut c_void, error: *const bindings::_xmlError) {
    if context.is_null() || error.is_null() {
        return;
    }
    let errors = &mut *(context as *mut Vec<String>);
    let error = &*error;
    let message = if error.message.is_null() {
        "error".into()
    } else {
        CStr::from_ptr(error.message).to_string_lossy()
    };
    let message = message.trim();
    errors.push(if error.line > 0 {
        format!("línea {}: {message}", error.line)
    } else {
        message.to_string()
    });
}

/// [`collect_error`] as libxml2's structured error callback. Its pointer
/// argument is `const` since libxml2 2.12 and mutable before; both have the
/// same ABI, so the cast works with bindings from either version.
#[allow(clippy::missing_transmute_annotations, clippy::useless_transmute)]
fn error_handler() -> bindings::xmlStructuredErrorFunc {
    let handler: unsafe extern "C" fn(*mut c_void, *const bindings::_xmlError) = collect_error;
    // SAFETY: same ABI (two pointers, no return value)
    Some(unsafe { std::mem::transmute(handler) })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn embedded_paths_resolve_relative_imports() {
        assert!(embedded_file(
            "/xhandler-embedded-xsd/2.1/maindoc/../common/UBL-CommonBasicComponents-2.1.xsd"
        )
        .is_some());
        assert!(embedded_file(
            "file:///xhandler-embedded-xsd/2.1/common/./UBL-QualifiedDataTypes-2.1.xsd"
        )
        .is_some());
        assert!(embedded_file("/xhandler-embedded-xsd/../etc/passwd").is_none());
        assert!(embedded_file("/etc/passwd").is_none());
        assert!(embedded_file("http://www.w3.org/2001/xml.xsd").is_none());
    }

    #[tokio::test]
    async fn every_schema_compiles_and_rejects_garbage() {
        init();
        for (document_type, _) in MAIN_SCHEMAS {
            let xml = format!("<{document_type}/>");
            let outcome = validate(document_type, xml.into_bytes()).await.unwrap();
            assert!(
                matches!(outcome, XsdOutcome::Invalid(_)),
                "{document_type}: {outcome:?}"
            );
        }
        assert_eq!(
            validate("Invoice", b"<Invoice>".to_vec()).await.unwrap(),
            XsdOutcome::Unparseable
        );
        assert_eq!(
            validate("Unknown", b"<Unknown/>".to_vec()).await.unwrap(),
            XsdOutcome::NoSchema
        );
    }
}
