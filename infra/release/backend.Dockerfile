# SPDX-FileCopyrightText: 2026 TRICORE contributors
# SPDX-License-Identifier: Apache-2.0
# Base image and image distribution obligations require separate R24 acceptance.
ARG JAVA_BASE
FROM ${JAVA_BASE}
WORKDIR /app
COPY --chown=10001:10001 application.jar /app/application.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
