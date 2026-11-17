//! Exposes the pinned xhandler-rust revision (Cargo.toml) to the code as
//! `XHANDLER_RUST_REV`, for `GET /v1/info`. One source of truth: the pin.

fn main() {
    println!("cargo:rerun-if-changed=Cargo.toml");
    let manifest = std::fs::read_to_string("Cargo.toml").expect("Cargo.toml is readable");
    let rev = xhandler_rev(&manifest).expect("Cargo.toml pins xhandler with rev = \"...\"");
    println!("cargo:rustc-env=XHANDLER_RUST_REV={rev}");
}

/// The `rev` of the `xhandler = { git = ..., rev = "..." }` dependency line
fn xhandler_rev(manifest: &str) -> Option<&str> {
    let line = manifest
        .lines()
        .find(|line| line.trim_start().starts_with("xhandler = {"))?;
    let after = &line[line.find("rev = \"")? + "rev = \"".len()..];
    Some(&after[..after.find('"')?])
}
