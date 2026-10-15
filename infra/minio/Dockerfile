# Build Community MinIO and mc from immutable upstream commits.
ARG GO_IMAGE=golang:1.25.3-bookworm
ARG RUNTIME_IMAGE=debian:bookworm-slim
FROM ${GO_IMAGE} AS build
ARG MINIO_COMMIT=9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a
ARG MC_COMMIT=77f82e18b5401a65958f1619df6ebb994634bd88
ENV CGO_ENABLED=0
ENV GOMAXPROCS=2 GOFLAGS="-p=2"
RUN git clone https://github.com/minio/minio.git /src/minio && cd /src/minio && git checkout --detach "${MINIO_COMMIT}" && test "$(git rev-parse HEAD)" = "${MINIO_COMMIT}" && go build -trimpath -o /out/minio .
RUN git clone https://github.com/minio/mc.git /src/mc && cd /src/mc && git checkout --detach "${MC_COMMIT}" && test "$(git rev-parse HEAD)" = "${MC_COMMIT}" && go build -trimpath -o /out/mc .
FROM ${RUNTIME_IMAGE}
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl jq && mkdir -p /data /home/minio && chown -R 10001:10001 /data /home/minio
COPY --from=build /out/minio /usr/local/bin/minio
COPY --from=build /out/mc /usr/local/bin/mc
ENV HOME=/home/minio
USER 10001:10001
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]
