//! Logs and traces. Traces are exported with OTLP over HTTP when
//! `OTEL_EXPORTER_OTLP_ENDPOINT` is set, and join the caller's trace through
//! the W3C `traceparent` header (DESIGN.md §15).

use axum::http::Request;
use opentelemetry::trace::TracerProvider as _;
use opentelemetry::{global, KeyValue};
use opentelemetry_http::HeaderExtractor;
use opentelemetry_sdk::propagation::TraceContextPropagator;
use opentelemetry_sdk::trace::SdkTracerProvider;
use opentelemetry_sdk::Resource;
use tracing::Span;
use tracing_opentelemetry::OpenTelemetrySpanExt;
use tracing_subscriber::layer::SubscriberExt;
use tracing_subscriber::util::SubscriberInitExt;
use tracing_subscriber::EnvFilter;

const SERVICE_NAME: &str = "xhandler-engine";

/// Initializes logging and, if configured, trace export. Call it before the
/// tokio runtime starts: the OTLP exporter uses a blocking HTTP client.
pub fn init() -> anyhow::Result<Option<SdkTracerProvider>> {
    let filter = EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info"));
    let logs = tracing_subscriber::fmt::layer().json();

    global::set_text_map_propagator(TraceContextPropagator::new());

    if std::env::var_os("OTEL_EXPORTER_OTLP_ENDPOINT").is_none() {
        tracing_subscriber::registry()
            .with(filter)
            .with(logs)
            .init();
        return Ok(None);
    }

    let exporter = opentelemetry_otlp::SpanExporter::builder()
        .with_http()
        .build()?;
    let provider = SdkTracerProvider::builder()
        .with_batch_exporter(exporter)
        .with_resource(
            Resource::builder()
                .with_service_name(SERVICE_NAME)
                .with_attribute(KeyValue::new("service.version", env!("CARGO_PKG_VERSION")))
                .build(),
        )
        .build();
    global::set_tracer_provider(provider.clone());

    tracing_subscriber::registry()
        .with(filter)
        .with(logs)
        .with(tracing_opentelemetry::layer().with_tracer(provider.tracer(SERVICE_NAME)))
        .init();
    Ok(Some(provider))
}

/// Span of an incoming request, child of the caller's span when the request
/// carries a `traceparent` header.
pub fn request_span<B>(request: &Request<B>) -> Span {
    let span = tracing::info_span!(
        "request",
        otel.kind = "server",
        http.request.method = %request.method(),
        url.path = %request.uri().path(),
    );
    let parent = global::get_text_map_propagator(|propagator| {
        propagator.extract(&HeaderExtractor(request.headers()))
    });
    let _ = span.set_parent(parent);
    span
}
