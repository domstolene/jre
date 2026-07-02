### FJERNE VERKTØY ###
# Fjern utviklerverktøy fra chiseled JRE, vi trenger kun java som entrypoint, ikke noe annet
FROM eclipse-temurin:25-jdk-alpine AS compiler

WORKDIR /build
ADD src/RemoveTools.java RemoveTools.java

RUN javac RemoveTools.java


### BYGGE IMAGE ###
# Minimalistisk Ubuntu med kun java (når vi har kjørt RemoveTools)
FROM docker.io/ubuntu/jre:25-26.04_edge AS chiseled


### FJERNE VERKTØY ###
# Hent RemoveTools.class fra compiler stage
COPY --from=compiler /build/RemoveTools.class /app/RemoveTools.class

# Kjør RemoveTools for å fjerne VERKTØY fra chiseled JRE
RUN ["java", "-cp", "/app", "RemoveTools"]


### ARGUMENTER ###
# siste versjon: gh release view --repo domstolene/da-otel-agent --json tagName --jq .tagName
ARG DA_OTEL_AGENT_VERSION=1.8.0


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
#
# for å opprette mappen med 755-rettigheter, kan ikke bruke mkdir da vi ikke har shell i dette imaget
WORKDIR /da-otel-agent
ADD --chmod=644 --chown=0:1000 https://github.com/domstolene/da-otel-agent/releases/download/${DA_OTEL_AGENT_VERSION}/da-opentelemetry-javaagent.jar da-otel-agent.jar
ADD --chmod=644 --chown=0:1000 src/da-otel-agent/da-otel-agent.yaml da-otel-agent.yaml

# skru av sending av metrikker og logger, som ikke støttes i vårt OpenTelemetry-oppsett per nå
ENV OTEL_METRICS_EXPORTER=none
ENV OTEL_LOGS_EXPORTER=none

# Som standard vil vi at docker image skal fungere uten tracing.
# OTEL_TRACES_SAMPLER settes til dynamic i kjøremiljøet, slik at
# konfigurasjonen i da-opentelemetry-javaagent.yaml blir aktiv
ENV OTEL_TRACES_SAMPLER=always_off


### OPPSTART DIREKTE VIA JAVA MED UPRIVILIGERT BRUKER ###
WORKDIR /app
USER 1000:1000
ENV SPRING_CONFIG_ADDITIONAL_LOCATION="optional:file:/config/,optional:file:/app/config/,optional:file:/deployments/config/"
# sørg for at stdout skriver med UTF-8
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8"

ENTRYPOINT ["java"]
CMD ["-javaagent:/da-otel-agent/da-otel-agent.jar", \
    "-Dotel.configuration.service.file=/da-otel-agent/da-otel-agent.yaml", \
    "-XX:InitialRAMPercentage=75.0", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", \
    "/app/application.jar" \
]
