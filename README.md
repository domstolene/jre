# JRE
Et base image for Java-applikasjoner i domstolene optimalisert for applikasjoner som skal kjøre i vår plattform, men minst mulig sikkerhetssårbarheter.

## Bruk
Bruk dette imaget som base image i applikasjonen din og legg jar-filen under /app eller /deployments:

```dockerfile
FROM ghcr.io/domstolene/jre
# eller Azul-variant:
# FROM ghcr.io/domstolene/jre:azul

# konfigurasjon i k8s-applications mountes til /deployments/config
WORKDIR /deployments

ADD --chmod=644 build/libs/*.jar ip-varsling-status.jar

# navn på tjenesten i sporingsoppsettet
ENV OTEL_SERVICE_NAME=ip-varsling-status
```

Se [Dockerfile](Dockerfile) for detaljene.

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
