//! The engine's SUNAT operations against the SUNAT fake, started in-process
//! on a random port (one fake per test, so their registries don't mix).

use axum::body::Body;
use axum::http::{Request, StatusCode};
use axum::Router;
use http_body_util::BodyExt;
use serde_json::{json, Value};
use tower::ServiceExt;
use xhandler_engine::api::{router_with, Config};
use xhandler_engine::sunat_fake::{self, Options};

struct Setup {
    engine: Router,
    fake: String,
}

async fn setup() -> Setup {
    let fake = sunat_fake::spawn(Options {
        timeout: std::time::Duration::from_secs(3),
    })
    .await
    .unwrap();
    Setup {
        engine: router_with(Config {
            sunat_fake_url: fake.clone(),
        }),
        fake,
    }
}

async fn call(app: &Router, request: Request<Body>) -> (StatusCode, Value) {
    let response = app.clone().oneshot(request).await.unwrap();
    let status = response.status();
    let bytes = response.into_body().collect().await.unwrap().to_bytes();
    (
        status,
        serde_json::from_slice(&bytes).unwrap_or(Value::Null),
    )
}

impl Setup {
    async fn post(&self, uri: &str, body: Value) -> (StatusCode, Value) {
        let request = Request::post(uri)
            .header("content-type", "application/json")
            .body(Body::from(body.to_string()))
            .unwrap();
        call(&self.engine, request).await
    }

    /// Calls the fake's admin API over HTTP
    async fn admin(&self, method: &str, path: &str, body: Option<Value>) -> Value {
        let client = reqwest::Client::new();
        let url = format!("{}{path}", self.fake);
        let request = match method {
            "GET" => client.get(url),
            _ => client.post(url),
        };
        let request = match body {
            Some(body) => request
                .header("content-type", "application/json")
                .body(body.to_string()),
            None => request,
        };
        let response = request.send().await.unwrap();
        assert!(response.status().is_success(), "{}", response.status());
        let text = response.text().await.unwrap();
        serde_json::from_str(&text).unwrap_or(Value::Null)
    }

    /// Builds and signs a document with the demo key, then puts the fake's
    /// scenario marker in an XML comment (comments aren't signed content)
    async fn signed(&self, document: Value, marker: Option<&str>) -> String {
        let (status, built) = self
            .post("/v1/build", json!({ "document": document }))
            .await;
        assert_eq!(status, StatusCode::OK, "{built}");
        let (status, signed) = self
            .post("/v1/sign", json!({ "xml": built["xml"], "demo_key": true }))
            .await;
        assert_eq!(status, StatusCode::OK, "{signed}");
        let xml = signed["xml"].as_str().unwrap().to_string();
        match marker {
            Some(marker) => {
                let end = xml.find("?>").map(|i| i + 2).unwrap_or(0);
                format!(
                    "{}<!-- xhandler-fake:{marker} -->{}",
                    &xml[..end],
                    &xml[end..]
                )
            }
            None => xml,
        }
    }

    async fn send(&self, xml: &str) -> Value {
        let (status, body) = self
            .post("/v1/send", json!({ "xml": xml, "environment": "fake" }))
            .await;
        assert_eq!(status, StatusCode::OK, "{body}");
        body
    }
}

fn invoice(serie_numero: &str) -> Value {
    json!({
        "kind": "Invoice",
        "spec": {
            "serie_numero": serie_numero,
            "proveedor": { "ruc": "12345678912", "razon_social": "Softgreen S.A.C." },
            "cliente": { "tipo_documento_identidad": "6", "numero_documento_identidad": "12121212121", "nombre": "Carlos Feria" },
            "detalles": [{ "descripcion": "Item1", "cantidad": 10, "precio": 100 }]
        }
    })
}

fn summary() -> Value {
    json!({
        "kind": "SummaryDocuments",
        "spec": {
            "numero": 1,
            "fecha_emision_comprobantes": "2026-11-14",
            "proveedor": { "ruc": "12345678912", "razon_social": "Softgreen S.A.C." },
            "comprobantes": [{
                "tipo_operacion": "1",
                "comprobante": {
                    "tipo_comprobante": "03",
                    "serie_numero": "B001-1",
                    "cliente": { "tipo_documento_identidad": "1", "numero_documento_identidad": "12345678" },
                    "valor_venta": { "importe_total": 118, "gravado": 100 },
                    "impuestos": { "igv": 18, "igv_tasa": 18 }
                }
            }]
        }
    })
}

fn despatch() -> Value {
    json!({
        "kind": "DespatchAdvice",
        "spec": {
            "serie_numero": "T001-1",
            "remitente": { "ruc": "12345678912", "razon_social": "Softgreen S.A.C." },
            "destinatario": { "tipo_documento_identidad": "1", "numero_documento_identidad": "12345678", "nombre": "Juan Perez" },
            "envio": {
                "tipo_traslado": "01",
                "peso_total": 25.5,
                "peso_total_unidad_medida": "KGM",
                "tipo_modalidad_traslado": "02",
                "fecha_traslado": "2099-01-16",
                "partida": { "ubigeo": "150101", "direccion": "Av. Los Olivos 123" },
                "destino": { "ubigeo": "040101", "direccion": "Jr. Arequipa 456" }
            },
            "detalles": [{ "unidad_medida": "NIU", "cantidad": 10, "codigo": "PROD001" }]
        }
    })
}

fn key(numero: u32) -> Value {
    json!({ "ruc": "12345678912", "tipo_comprobante": "01", "serie": "F001", "numero": numero })
}

#[tokio::test]
async fn send_accepted() {
    let s = setup().await;
    let body = s.send(&s.signed(invoice("F001-1"), None).await).await;
    assert_eq!(body["status"], "accepted", "{body}");
    assert_eq!(body["code"], 0);
    assert_eq!(body["code_class"], Value::Null);
    assert!(body["cdr_zip"].is_string());
}

#[tokio::test]
async fn send_rejected_and_observed() {
    let s = setup().await;
    let body = s
        .send(&s.signed(invoice("F001-1"), Some("reject")).await)
        .await;
    assert_eq!(
        (&body["status"], &body["code"]),
        (&json!("rejected"), &json!(2335)),
        "{body}"
    );
    assert!(body["cdr_zip"].is_string());

    let body = s
        .send(&s.signed(invoice("F001-2"), Some("observe")).await)
        .await;
    assert_eq!(
        (&body["status"], &body["code"]),
        (&json!("accepted"), &json!(4252)),
        "{body}"
    );
    assert_eq!(body["notes"].as_array().unwrap().len(), 1, "{body}");
}

#[tokio::test]
async fn send_faults_are_classified() {
    let s = setup().await;
    let body = s
        .send(&s.signed(invoice("F001-1"), Some("exception")).await)
        .await;
    assert_eq!(body["status"], "exception", "{body}");
    assert_eq!(body["code"], 109);
    assert_eq!(body["code_class"], "transient_system");

    let body = s
        .send(&s.signed(invoice("F001-2"), Some("collision")).await)
        .await;
    assert_eq!(body["code"], 1033, "{body}");
    assert_eq!(body["code_class"], "already_registered");

    let body = s
        .send(&s.signed(invoice("F001-3"), Some("credentials")).await)
        .await;
    assert_eq!(body["code"], 102, "{body}");
    assert_eq!(body["code_class"], "permanent_credentials");

    // Wrong password for the MODDATOS user
    let (_, body) = s
        .post(
            "/v1/send",
            json!({
                "xml": s.signed(invoice("F001-4"), None).await,
                "environment": "fake",
                "credentials": { "username": "12345678912MODDATOS", "password": "wrong" },
            }),
        )
        .await;
    assert_eq!(body["code"], 102, "{body}");
}

#[tokio::test]
async fn resending_same_bytes_is_accepted_other_bytes_collide() {
    let s = setup().await;
    let first = s.signed(invoice("F001-7"), None).await;
    assert_eq!(s.send(&first).await["status"], "accepted");
    let again = s.send(&first).await;
    assert_eq!(again["status"], "accepted", "{again}");

    // Same number, other bytes (another marker comment)
    let other = s.signed(invoice("F001-7"), Some("accept")).await;
    let body = s.send(&other).await;
    assert_eq!(
        (&body["status"], &body["code"]),
        (&json!("exception"), &json!(1033)),
        "{body}"
    );
}

#[tokio::test]
async fn dropped_answer_is_unknown_and_sunat_has_the_document() {
    let s = setup().await;
    let xml = s.signed(invoice("F001-8"), Some("drop")).await;
    let body = s.send(&xml).await;
    assert_eq!(body["status"], "unknown", "{body}");

    let received = s.admin("GET", "/fake/received", None).await;
    assert_eq!(received.as_array().unwrap().len(), 1, "{received}");

    // Resolving the ambiguity: SUNAT has it, accepted
    let (_, cdr) = s
        .post(
            "/v1/status-cdr",
            json!({ "environment": "fake", "document": key(8) }),
        )
        .await;
    assert_eq!(cdr["status"], "accepted", "{cdr}");
    assert_eq!(cdr["code"], "0004");
    assert!(cdr["cdr_zip"].is_string());
    // and the same bytes get the same answer
    assert_eq!(s.send(&xml).await["status"], "accepted");
}

/// SUNAT's proxy answering 503 with HTML: the request reached SUNAT's front,
/// so it's `unknown` (xsender: Response(503)); only connection failures and
/// the proxy's rate-limit 401 are `not_delivered`.
#[tokio::test]
async fn outage_is_unknown_and_connection_refused_is_not_delivered() {
    let s = setup().await;
    s.admin("POST", "/fake/mode", Some(json!({ "mode": "outage" })))
        .await;
    let xml = s.signed(invoice("F001-9"), None).await;
    let body = s.send(&xml).await;
    assert_eq!(body["status"], "unknown", "{body}");
    let (_, cdr) = s
        .post(
            "/v1/status-cdr",
            json!({ "environment": "fake", "document": key(9) }),
        )
        .await;
    assert_eq!(cdr["status"], "unknown", "{cdr}");

    s.admin("POST", "/fake/mode", Some(json!({ "mode": "normal" })))
        .await;
    assert_eq!(s.send(&xml).await["status"], "accepted");

    // Nothing listens on the discard port
    let (_, body) = s
        .post(
            "/v1/send",
            json!({ "xml": xml, "environment": "beta", "urls": { "invoice": "http://127.0.0.1:9/billService" } }),
        )
        .await;
    assert_eq!(body["status"], "not_delivered", "{body}");
}

#[tokio::test]
async fn urls_override_the_environment() {
    let s = setup().await;
    let xml = s.signed(invoice("F001-10"), None).await;
    let (_, body) = s
        .post(
            "/v1/send",
            json!({
                "xml": xml,
                "environment": "beta",
                "urls": { "invoice": format!("{}/ol-ti-itcpfegem-beta/billService", s.fake) },
            }),
        )
        .await;
    assert_eq!(body["status"], "accepted", "{body}");
}

#[tokio::test]
async fn summary_ticket_is_verified() {
    let s = setup().await;
    let xml = s.signed(summary(), None).await;
    let body = s.send(&xml).await;
    assert_eq!(body["status"], "in_progress", "{body}");
    let ticket = body["ticket"].as_str().unwrap().to_string();

    let (status, body) = s
        .post(
            "/v1/verify-ticket",
            json!({ "xml": xml, "ticket": ticket, "environment": "fake" }),
        )
        .await;
    assert_eq!(status, StatusCode::OK, "{body}");
    assert_eq!(body["status"], "accepted", "{body}");
    assert_eq!(body["code"], 0);
    assert!(body["cdr_zip"].is_string());

    let (_, body) = s
        .post(
            "/v1/verify-ticket",
            json!({ "xml": xml, "ticket": "999", "environment": "fake" }),
        )
        .await;
    assert_eq!(body["code_class"], "ticket_lost", "{body}");
}

#[tokio::test]
async fn summary_ticket_in_progress() {
    let s = setup().await;
    let xml = s.signed(summary(), Some("in_progress")).await;
    let ticket = s.send(&xml).await["ticket"].clone();
    let (_, body) = s
        .post(
            "/v1/verify-ticket",
            json!({ "xml": xml, "ticket": ticket, "environment": "fake" }),
        )
        .await;
    assert_eq!(
        (&body["status"], &body["code"]),
        (&json!("in_progress"), &json!(98)),
        "{body}"
    );
}

#[tokio::test]
async fn status_cdr_and_validity() {
    let s = setup().await;
    let (_, body) = s
        .post(
            "/v1/status-cdr",
            json!({ "environment": "fake", "document": key(11) }),
        )
        .await;
    assert_eq!(
        (&body["status"], &body["code"]),
        (&json!("not_found"), &json!("0011")),
        "{body}"
    );

    s.send(&s.signed(invoice("F001-11"), None).await).await;
    let (_, body) = s
        .post(
            "/v1/status-cdr",
            json!({ "environment": "fake", "document": key(11) }),
        )
        .await;
    assert_eq!(body["status"], "accepted", "{body}");

    let validity = |numero: u32| {
        json!({
            "environment": "fake", "document": key(numero), "receptor_tipo": "6",
            "receptor_numero": "12121212121", "fecha_emision": "2026-11-15", "importe_total": "1180.00",
        })
    };
    let (_, body) = s.post("/v1/validity", validity(11)).await;
    assert_eq!(body["state"], "valid", "{body}");
    let (_, body) = s.post("/v1/validity", validity(12)).await;
    assert_eq!(body["state"], "not_found", "{body}");
}

#[tokio::test]
async fn validity_file_compares_the_bytes() {
    let s = setup().await;
    let xml = s.signed(invoice("F001-13"), None).await;
    let check = |xml: String| json!({ "xml": xml, "environment": "fake" });

    let (_, body) = s.post("/v1/validity/file", check(xml.clone())).await;
    assert_eq!(body["state"], "not_found", "{body}");

    s.send(&xml).await;
    let (_, body) = s.post("/v1/validity/file", check(xml)).await;
    assert_eq!(body["state"], "matches", "{body}");

    let other = s.signed(invoice("F001-13"), Some("accept")).await;
    let (_, body) = s.post("/v1/validity/file", check(other)).await;
    assert_eq!(body["state"], "differs", "{body}");
}

#[tokio::test]
async fn despatch_advice_goes_through_gre_rest() {
    let s = setup().await;
    let xml = s.signed(despatch(), None).await;
    let body = s.send(&xml).await;
    assert_eq!(body["status"], "in_progress", "{body}");
    let ticket = body["ticket"].clone();

    let (_, body) = s
        .post(
            "/v1/verify-ticket",
            json!({ "xml": xml, "ticket": ticket, "environment": "fake" }),
        )
        .await;
    assert_eq!(body["status"], "accepted", "{body}");
    assert!(body["cdr_zip"].is_string());

    // GRE needs an API client (beta's default user has none)
    let (status, body) = s
        .post(
            "/v1/send",
            json!({
                "xml": xml,
                "environment": "beta",
                "urls": { "despatch": format!("{}/v1/contribuyente/gem", s.fake) },
            }),
        )
        .await;
    assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY);
    assert_eq!(body["kind"], "invalid_request", "{body}");
}

#[tokio::test]
async fn fake_reset_forgets_everything() {
    let s = setup().await;
    s.send(&s.signed(invoice("F001-14"), None).await).await;
    s.admin("POST", "/fake/reset", None).await;
    let received = s.admin("GET", "/fake/received", None).await;
    assert!(received.as_array().unwrap().is_empty());
}

fn api_credentials() -> Value {
    json!({ "client_id": "xhandler-fake-client", "client_secret": "xhandler-fake-secret",
            "username": "12345678912USUARIO1", "password": "secret" })
}

#[tokio::test]
async fn sire_round_trip_and_conformity() {
    use base64::Engine as _;
    use std::io::{Read, Write};
    let s = setup().await;
    s.send(&s.signed(invoice("F001-21"), None).await).await;
    let period = chrono::Local::now().format("%Y%m").to_string();

    // SUNAT's proposal lists the document the fake accepted
    let (status, body) = s
        .post(
            "/v1/sire/proposal",
            json!({ "environment": "fake", "credentials": api_credentials(), "book": "rvie", "period": period }),
        )
        .await;
    assert_eq!(status, StatusCode::OK, "{body}");
    assert_eq!(body["state"], "ok", "{body}");
    let ticket = body["ticket"].as_str().unwrap().to_string();
    let (_, body) = s
        .post(
            "/v1/sire/ticket",
            json!({ "environment": "fake", "credentials": api_credentials(), "book": "rvie", "period": period, "ticket": ticket }),
        )
        .await;
    assert_eq!(body["ticket_state"], "done", "{body}");
    let zip = base64::engine::general_purpose::STANDARD
        .decode(body["files"][0]["content_base64"].as_str().unwrap())
        .unwrap();
    let mut archive = zip::ZipArchive::new(std::io::Cursor::new(zip)).unwrap();
    let mut text = String::new();
    archive
        .by_index(0)
        .unwrap()
        .read_to_string(&mut text)
        .unwrap();
    assert!(text.lines().any(|l| l.contains("|01|F001|21|")), "{text}");

    // A replacement goes up with its hash and gets a ticket
    let mut writer = zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
    writer
        .start_file("LE.txt", zip::write::SimpleFileOptions::default())
        .unwrap();
    writer.write_all(text.as_bytes()).unwrap();
    let replacement = writer.finish().unwrap().into_inner();
    let (_, body) = s
        .post(
            "/v1/sire/replace",
            json!({ "environment": "fake", "credentials": api_credentials(), "book": "rvie", "period": period,
                    "file_name": "LE.zip", "zip_base64": base64::engine::general_purpose::STANDARD.encode(replacement) }),
        )
        .await;
    assert_eq!(body["state"], "ok", "{body}");
    let submitted = s.admin("GET", "/fake/sire", None).await;
    assert_eq!(submitted[0]["rows"], 1, "{submitted}");

    // Conformity of the factura: pending until the buyer answers; unknown documents are not found
    let conformity = |numero: u32| {
        json!({ "environment": "fake", "credentials": api_credentials(), "ruc": "12345678912",
                "tipo_comprobante": "01", "serie": "F001", "numero": numero })
    };
    let (_, body) = s.post("/v1/conformity", conformity(21)).await;
    assert_eq!(body["conformity"], "pending", "{body}");
    let (_, body) = s.post("/v1/conformity", conformity(22)).await;
    assert_eq!(body["conformity"], "not_found", "{body}");

    // Bad API credentials, and no SIRE in beta
    let (_, body) = s
        .post(
            "/v1/sire/proposal",
            json!({ "environment": "fake", "credentials": { "client_id": "x", "client_secret": "", "username": "u", "password": "p" },
                    "book": "rce", "period": period }),
        )
        .await;
    assert_eq!(body["state"], "unauthorized", "{body}");
    let (status, _) = s
        .post(
            "/v1/sire/proposal",
            json!({ "environment": "beta", "credentials": api_credentials(), "book": "rvie", "period": period }),
        )
        .await;
    assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY);
}
