# JRE
Et base image for Java-applikasjoner i domstolene optimalisert for applikasjoner som skal kjøre i vår plattform, men minst mulig sikkerhetssårbarheter og mest mulig ferdigkonfigurert for deg.

## Bruk
Bruk dette imaget som base image i applikasjonen din og legg jar-filen under `/app/application.jar`:

```dockerfile
FROM ghcr.io/domstolene/jre:chiseled

# navn på tjenesten i sporingsoppsettet
ENV OTEL_SERVICE_NAME=ip-varsling-status

# WORKDIR er /app, slik at den havner i /app/application.jar
ADD --chmod=644 --chown=0:1000 build/libs/*.jar application.jar
```

OBS: Ikke overstyr `WORKDIR`, det er satt til `/app` og application.jar havner da i `/app/application.jar`.
PS: Tross dette vil Spring Boot finne konfigurasjon i `/config`, `/app/config` og `/deployments/config`. Se `SPRING_CONFIG_ADDITIONAL_LOCATION` i [Dockerfile](Dockerfile) for detaljer.


I pipeline, sørg for å alltid hente siste versjon av base image med `pull: true`:

```yaml
- name: Build latest docker image
  uses: docker/build-push-action@v7
  with:
    context: .
    pull: true # hent alltid base image
    push: true
    tags:
```


## Feilsøking
For å feilsøke en applikasjon som bruker dette imaget trenger en å starte en midlertidig container ved siden av applikasjonen, ettersom applikasjonen har _null_ verktøy for feilsøking.

Du kan starte en debug-container som dette:
```shell
export app=ip-varsling-status  # må også være container-navnet, eventuelt sett containernavn i --target
export namespace=ip-varsling   # ofte samme som app, men ulikt for ip-varsling (flere deployments)
export pod=$(kubectl get pods --namespace $namespace -o name | grep $app | head -n1)

kubectl debug --namespace $namespace $pod \
  --target=$app \
  --image=docker.io/eclipse-temurin:25-jdk \
  --tty --stdin \
  -- bash
```

MERK: `oc debug` starter en debug-**pod**, så det fungerer ikke til å inspisere en kjørende applikasjon.

Inni debug-containeren kan du nå kjøre kommandoer og sjekke ting:
```shell
# se kjørende prosesser
ps aux

# printe stacken til applikasjonen
jcmd 1 Thread.print

# se på filsystemet til applikasjonen
cd /proc/1/root
ls -l

# se miljøvariabler i poden
cat /proc/1/environ | tr '\0' '\n'
```

Shell og ønsket inspeksjon kan også kombineres som dette:
```shell
# henter miljøvariabelen DB_PASSWORD
kubectl debug --namespace $namespace $pod \
  --target=$app \
  --quiet --stdin --tty \
  --image=eclipse-temurin:25-jdk \
  -- sh -c "cat /proc/1/environ | tr '\\0' '\\n'" \
  | grep DB_PASSWORD
```

Dersom du trenger `curl`, kan du bruke [Red Hat Universal Base Image](https://catalog.redhat.com/en/software/base-images):
```shell
kubectl debug --namespace $namespace $pod \
  --target=$app \
  --image=ubi10 \
  --tty --stdin \
  -- bash
```


## Sikkerhet
Chiseled er et verktøy som [stripper Ubuntu helt ned](https://hub.docker.com/r/ubuntu/jre), slik at en kun har Java for å kjøre applikasjoner og ikke noe annet. Ingen pakker, ingen shell, ingenting annet enn det som trengs. Det medfører at vi ikke trenger bekymre oss for sårbarheter og angrep, fordi de oftes gjøres via flere steg:

1. Sårbarhet i applikasjonen.
2. Tilgjengelige verktøy, slik som `sh` eller `curl` i image.
3. Sårbarheter i tilgjengelige verktøy i image.

Når vi har fjernet steg 2, alle verktøy som finnes i image, så er det svært vanskelig å angripe en applikasjon.

Legg merke til at ubuntu/jre også inneholder kompilatoren `javac` og mange verktøy for feilsøking, slik som `jcmd`. De fjerner vi med [RemoveTools.java](src/RemoveTools.java), slik at en angriper ikke kan lage programmer med ren java-kode, kompilere den, så kjøre koden. Eller inspisere og endre på java-prosessen runtime.

Se [seksjonen om feilsøking](#feilsøking) for hvordan en bruker verktøy som `jcmd` for å printe stacken.


## Sikkerhetsvarsler og scanning
Scanne med Red Hat Advanced Cluster Security:

```shell
export ROX_ENDPOINT=acs-ui.apps.ocp.mgmt.domstol.no:443
export ROX_API_TOKEN=<fås fra plattformteamet, se https://domstol.atlassian.net/browse/PLATTFORM-3192>
roxctl central login
roxctl image scan --image docker.io/ubuntu/jre:25-26.04_edge --output json
```

Sjekke images som allerede er scannet:
```shell
roxctl image check --image docker.io/ubuntu/jre:25-26.04_edge --output json
```

Andre verktøy som er verdt å nevne er [trivy](https://github.com/aquasecurity/trivy) og [syft](https://github.com/anchore/syft).
