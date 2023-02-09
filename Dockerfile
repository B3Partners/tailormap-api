#
# Copyright (C) 2021 B3Partners B.V.
#
# SPDX-License-Identifier: MIT
#
FROM eclipse-temurin:11.0.18_10-jre

ARG TAILORMAP_API_VERSION="10.0.0-rc2-SNAPSHOT"
ARG TZ="Europe/Amsterdam"
ARG DEBIAN_FRONTEND="noninteractive"

LABEL org.opencontainers.image.authors="support@b3partners.nl" \
      org.opencontainers.image.description="Tailormap API service provides OpenAPI REST interface for Tailormap" \
      org.opencontainers.image.vendor="B3Partners BV" \
      org.opencontainers.image.title="Tailormap API" \
      org.opencontainers.image.url="https://github.com/B3Partners/tailormap-api/" \
      org.opencontainers.image.source="https://github.com/B3Partners/tailormap-api/" \
      org.opencontainers.image.documentation="https://github.com/B3Partners/tailormap-api/" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.version=$TAILORMAP_API_VERSION

# set-up timezone and local user
RUN set -eux;ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
    && apt update && apt upgrade -y && apt autoremove -y && apt autoclean && apt clean && rm -rf /tmp/* && rm -rf /var/tmp/* && rm -rf /var/lib/apt/lists/* \
    && useradd -ms /bin/bash spring

USER spring:spring

WORKDIR /home/spring

COPY ./target/tailormap-api-exec.jar tailormap-api.jar

EXPOSE 8080
# see https://docs.spring.io/spring-boot/docs/current/actuator-api/htmlsingle/#health
HEALTHCHECK --interval=25s --timeout=3s --retries=2 CMD wget -nv --spider --tries=1 --no-verbose --header 'Accept: application/json' http://127.0.0.1:8081/api/actuator/health || exit 1

# note that Spring Boot logs to the console, there is no logfile
ENTRYPOINT ["java", "-jar", "tailormap-api.jar"]
