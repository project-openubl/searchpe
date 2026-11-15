# Engine image: the Rust binary on a distroless base (glibc + CA certificates).
FROM docker.io/library/rust:1.100-bookworm AS build
WORKDIR /src
# Dependencies first, so source changes don't rebuild them
COPY Cargo.toml Cargo.lock rust-toolchain.toml ./
RUN mkdir src && echo "fn main() {}" > src/main.rs \
    && cargo build --release --locked \
    && rm -rf src
COPY . .
# COPY keeps the host's (older) mtimes: touch so cargo rebuilds over the placeholder
RUN touch src/main.rs && cargo build --release --locked

FROM gcr.io/distroless/cc-debian12:nonroot
COPY --from=build /src/target/release/xhandler-engine /usr/local/bin/xhandler-engine
EXPOSE 8090
ENTRYPOINT ["/usr/local/bin/xhandler-engine"]
