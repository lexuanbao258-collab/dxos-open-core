# DX-OS backend packaging only. Build/test and DB provisioning are separate gates.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src
COPY . .
# Always use the repository-pinned Gradle wrapper, not a system Gradle version.
RUN sh ./gradlew --no-daemon bootJar -x test --console=plain

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd --system --gid 10001 dxos \
    && useradd --system --uid 10001 --gid 10001 --no-create-home dxos \
    && mkdir -p /var/lib/dxos/storage \
    && chown dxos:dxos /var/lib/dxos/storage
COPY --from=build --chown=10001:10001 /src/build/libs/*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
