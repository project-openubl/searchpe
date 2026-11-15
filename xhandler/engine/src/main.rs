//! xhandler-engine: the Rust sidecar of xhandler-service (DESIGN.md §4).

mod api;
mod telemetry;

use tower_http::trace::{DefaultOnResponse, TraceLayer};
use tracing::Level;

/// Listens on localhost by default: the engine is a sidecar reached only by
/// the Quarkus container of the same pod.
const DEFAULT_ADDR: &str = "127.0.0.1:8090";

fn main() -> anyhow::Result<()> {
    // `xhandler-engine --health`: probe for Kubernetes. The engine only listens
    // on localhost, which kubelet HTTP probes can't reach, and the distroless
    // image has no curl.
    if std::env::args().any(|arg| arg == "--health") {
        std::process::exit(if health_check(&addr()) { 0 } else { 1 });
    }

    let tracer_provider = telemetry::init()?;

    let result = tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()?
        .block_on(serve());

    if let Some(provider) = tracer_provider {
        if let Err(e) = provider.shutdown() {
            eprintln!("failed to flush traces: {e}");
        }
    }
    result
}

fn addr() -> String {
    std::env::var("XHANDLER_ENGINE_ADDR").unwrap_or_else(|_| DEFAULT_ADDR.to_string())
}

/// GET /health on the running engine; true when it answers 200.
fn health_check(addr: &str) -> bool {
    use std::io::{Read, Write};
    use std::time::Duration;

    use std::net::ToSocketAddrs;

    let Some(socket) = addr
        .to_socket_addrs()
        .ok()
        .and_then(|mut addrs| addrs.next())
    else {
        return false;
    };
    let Ok(mut stream) = std::net::TcpStream::connect_timeout(&socket, Duration::from_secs(2))
    else {
        return false;
    };
    let _ = stream.set_read_timeout(Some(Duration::from_secs(2)));
    if stream
        .write_all(b"GET /health HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
        .is_err()
    {
        return false;
    }
    let mut response = String::new();
    let _ = stream.read_to_string(&mut response);
    response.starts_with("HTTP/1.1 200")
}

async fn serve() -> anyhow::Result<()> {
    let addr = addr();
    let app = api::router().layer(
        TraceLayer::new_for_http()
            .make_span_with(telemetry::request_span)
            .on_response(DefaultOnResponse::new().level(Level::INFO)),
    );

    let listener = tokio::net::TcpListener::bind(&addr).await?;
    tracing::info!(%addr, "xhandler-engine listening");
    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await?;
    Ok(())
}

async fn shutdown_signal() {
    let ctrl_c = async {
        let _ = tokio::signal::ctrl_c().await;
    };
    #[cfg(unix)]
    let terminate = async {
        if let Ok(mut signal) =
            tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
        {
            signal.recv().await;
        }
    };
    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        _ = ctrl_c => {},
        _ = terminate => {},
    }
    tracing::info!("shutting down");
}
