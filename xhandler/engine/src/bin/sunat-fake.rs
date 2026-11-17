//! sunat-fake: SUNAT's services for tests, development and chaos runs
//! (DESIGN.md §15.2). See `xhandler_engine::sunat_fake` for the scenarios.
//!
//!   sunat-fake [--addr 127.0.0.1:8099] [--timeout-seconds 130]

use std::time::Duration;

use xhandler_engine::sunat_fake::{self, Options};

const DEFAULT_ADDR: &str = "127.0.0.1:8099";

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "info".into()),
        )
        .init();

    let args: Vec<String> = std::env::args().collect();
    let flag = |name: &str| {
        args.iter()
            .position(|a| a == name)
            .and_then(|i| args.get(i + 1).cloned())
    };
    let addr = flag("--addr").unwrap_or_else(|| DEFAULT_ADDR.to_string());
    let mut options = Options::default();
    if let Some(seconds) = flag("--timeout-seconds") {
        options.timeout = Duration::from_secs(seconds.parse()?);
    }

    let listener = tokio::net::TcpListener::bind(&addr).await?;
    tracing::info!(%addr, "SUNAT fake listening");
    axum::serve(listener, sunat_fake::router(options))
        .with_graceful_shutdown(async {
            let _ = tokio::signal::ctrl_c().await;
        })
        .await?;
    Ok(())
}
