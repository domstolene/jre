FROM docker.io/eclipse-temurin:25-jre


### ARGUMENTER ###
# siste versjon: gh release view --repo domstolene/da-otel-agent --json tagName --jq .tagName
ARG DA_OTEL_AGENT_VERSION=1.7.9


### OPPDATERTE PAKKER ###
RUN apt-get update && apt-get upgrade -y && rm -rf /var/lib/apt/lists/* \
    && mkdir /app /deployments \
    && chown ubuntu:ubuntu /app /deployments


### SPORING ###
# da-otel-agent gir oss sporing der en kan skru av/på sporingen på
# https://otelconfig.apps.ocp.test.domstol.no uten å omstarte tjenesten
#
# For å teste sporing lokalt, kan du:
# 1. starte [otel-desktop-viewer](https://github.com/CtrlSpice/otel-desktop-viewer):
#
#    otel-desktop-viewer
#
# 2. Kjøre docker imaget med disse miljøvariablene:
#
#    docker run -e OTEL_TRACES_SAMPLER=always_on \
#      -e OTEL_EXPORTER_OTLP_ENDPOINT=http://host.docker.internal:4318 \
#      -e OTEL_TRACES_EXPORTER=otlp \
#      -e OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf \
#      <docker-image> # for eksempel ghcr.io/domstolene/ip-ap-api:latest
ADD --chmod=644 https://github.com/domstolene/da-otel-agent/releases/download/${DA_OTEL_AGENT_VERSION}/da-opentelemetry-javaagent.jar /da-otel-agent/da-otel-agent.jar

# skru av sending av metrikker og logger, som ikke støttes i vårt OpenTelemetry-oppsett per nå
ENV OTEL_METRICS_EXPORTER=none
ENV OTEL_LOGS_EXPORTER=none

# Som standard vil vi at docker image skal fungere uten tracing.
# OTEL_TRACES_SAMPLER settes til dynamic i kjøremiljøet, slik at
# konfigurasjonen i da-opentelemetry-javaagent.yaml blir aktiv
ENV OTEL_TRACES_SAMPLER=always_off


### ENTRYPOINT OG KONFIGURASJON AV SPORING ###
COPY src /


### KJØR SOM BRUKER, IKKE ROOT ###
USER ubuntu


### OPPSTART SOM FINNER .jar-FIL OG STØTTER JAVA_OPTS ###
ENTRYPOINT ["/entrypoint.sh"]
