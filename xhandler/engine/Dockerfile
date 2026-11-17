# Engine image: the Rust binaries on a distroless base (glibc + CA certificates)
# plus libxml2 for XSD validation (DESIGN.md §19 R5).
#   /usr/local/bin/xhandler-engine  the sidecar (default entrypoint)
#   /usr/local/bin/sunat-fake       the SUNAT fake for tests and chaos runs
FROM docker.io/library/rust:1.100-bookworm AS build
# libxml2 headers + libclang: the libxml crate generates its bindings at build time
RUN apt-get update \
    && apt-get install -y --no-install-recommends libxml2-dev libclang-dev pkg-config \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /src
# Dependencies first, so source changes don't rebuild them
COPY Cargo.toml Cargo.lock rust-toolchain.toml build.rs ./
RUN mkdir src && echo "fn main() {}" > src/main.rs \
    && cargo build --release --locked \
    && rm -rf src
COPY . .
# COPY keeps the host's (older) mtimes: touch so cargo rebuilds over the placeholder
RUN find src -name '*.rs' -exec touch {} + && cargo build --release --locked --bins
# The shared libraries libxml2 needs that distroless/cc lacks (ICU, lzma, zlib)
RUN mkdir /runtime-libs \
    && ldd target/release/xhandler-engine | awk '/=> \// { print $3 }' | while read -r lib; do \
         case "$(basename "$lib")" in \
           libc.so*|libm.so*|libdl.so*|libpthread.so*|librt.so*|libgcc_s.so*|libstdc++.so*|ld-linux*) ;; \
           *) cp -L "$lib" /runtime-libs/ ;; \
         esac; \
       done \
    && ls /runtime-libs | grep -q libxml2

FROM gcr.io/distroless/cc-debian12:nonroot
COPY --from=build /runtime-libs/ /usr/lib/
COPY --from=build /src/target/release/xhandler-engine /usr/local/bin/xhandler-engine
COPY --from=build /src/target/release/sunat-fake /usr/local/bin/sunat-fake
EXPOSE 8090
ENTRYPOINT ["/usr/local/bin/xhandler-engine"]
