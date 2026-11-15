# Engine image: static-ish Rust binary on a distroless base (glibc + CA certs).
FROM docker.io/library/rust:1.100-bookworm AS build
WORKDIR /src
COPY . .
RUN cargo build --release --locked

FROM gcr.io/distroless/cc-debian12:nonroot
COPY --from=build /src/target/release/xhandler-engine /usr/local/bin/xhandler-engine
# Sidecar: the Quarkus container reaches it over the pod's localhost
ENV XHANDLER_ENGINE_ADDR=127.0.0.1:8090
EXPOSE 8090
ENTRYPOINT ["/usr/local/bin/xhandler-engine"]
