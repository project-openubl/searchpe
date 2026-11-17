# Builder image
FROM registry.access.redhat.com/ubi9/nodejs-22:latest AS builder

USER 1001
COPY --chown=1001 . .
RUN \
  npm version && \
  npm config ls && \
  npm ci --verbose --ignore-scripts --no-audit && \
  npm run generate && \
  npm run build && \
  npm run dist

# Runner image
FROM registry.access.redhat.com/ubi9/nodejs-22-minimal:latest

# Add ps package to allow liveness probe for k8s cluster
# Add tar package to allow copying files with kubectl scp
USER 0
RUN microdnf -y install tar procps-ng && microdnf clean all

USER 1001

LABEL name="openubl/openubl-ui" \
      description="Openubl - User Interface (xhandler-service and searchpe)" \
      help="For more information visit https://project-openubl.github.io/" \
      license="Apache License 2.0" \
      summary="Openubl - User Interface" \
      url="https://github.com/project-openubl/searchpe" \
      usage="podman run -p 8080 -e XHANDLER_API_URL=http://xhandler:8080 -e SEARCHPE_API_URL=http://searchpe:8080 -e OIDC_SERVER_URL=https://sso/realms/xhandler openubl-ui" \
      io.k8s.display-name="openubl-ui" \
      io.k8s.description="Openubl - User Interface" \
      io.openshift.expose-services="8080:http" \
      io.openshift.tags="openubl,xhandler,searchpe,ui,nodejs22" \
      io.openshift.min-cpu="100m" \
      io.openshift.min-memory="350Mi"

COPY --from=builder /opt/app-root/src/dist /opt/app-root/dist/

# Release version surfaced in the UI's About modal (ENV.VERSION). Supplied by the image
# build from the release tag; defaults to the dev placeholder when unset.
ARG VERSION=99.0.0
ENV VERSION=$VERSION


WORKDIR /opt/app-root/dist
ENTRYPOINT ["./entrypoint.sh"]
