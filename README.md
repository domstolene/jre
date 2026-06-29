# JRE
Et base image for Java-applikasjoner i domstolene optimalisert for applikasjoner som skal kjøre i vår plattform, men minst mulig sikkerhetssårbarheter.

## Bruk
Bruk dette imaget som base image i applikasjonen din og legg jar-filen under /app eller /deployments:

```dockerfile
FROM ghcr.io/domstolene/jre
# eller Azul-variant:
# FROM ghcr.io/domstolene/jre:azul

# navn på tjenesten i sporingsoppsettet
ENV OTEL_SERVICE_NAME=ip-varsling-status

# konfigurasjon i k8s-applications mountes til /deployments/config
WORKDIR /deployments
ADD --chmod=644 build/libs/*.jar ip-varsling-status.jar
```

Se [Dockerfile](Dockerfile) for detaljene.

### Minimalt image
Chiseled er et verktøy som stripper Ubuntu helt ned, slik at en kun har [pebble](https://documentation.ubuntu.com/rockcraft/latest/explanation/pebble/) som oppstart og ikke noe annet. Ingen pakker, ingen shell, ingenting annet enn det som trengs.

Siden det ikke har noe shell og oppstarten dermed ikke kan dynamisk finne jar-filen, **må** navnet på applikasjonens jar være `/app/application.jar`:

```Dockerfile
FROM ghcr.io/domstolene/jre:chiseled

# navn på tjenesten i sporingsoppsettet
ENV OTEL_SERVICE_NAME=ip-varsling-status

ADD --chmod=644 --chown=0:1000 build/libs/*.jar application.jar
```

OBS: Ikke bruk `WORKDIR`, det er satt til `/app` og application.jar havner da i `/app/application.jar`.
PS: Tross dette vil Spring Boot lete etter konfigurasjon i `/config`, `/app/config` og `/deployments/config`. Se [Dockerfile.chiseled](Dockerfile.chiseled) for detaljer.

## Sikkerhetsvarsler
Scanne med Red Hat Advanced Cluster Security (når tilganger er på plass):

```shell
export ROX_ENDPOINT=acs-ui.apps.ocp.mgmt.domstol.no:443
roxctl central login
roxctl image scan --image docker.io/ubuntu/jre:25-26.04_edge --output json
```

Sjekke images som allerede er scannet:
```shell
roxctl image check --image docker.io/ubuntu/jre:25-26.04_edge --output json
```
