[![Build Status](https://github.com/domstolene/jre/actions/workflows/build-and-publish.yml/badge.svg?branch=main)](https://github.com/domstolene/jre/actions/workflows/build-and-publish.yml)
[![Byggerapport](byggerapport.svg)](https://domstolene.github.io/jre/)
![Siste bygg](https://domstolene.github.io/jre/digest.svg)


# JRE
Et base image for Java-applikasjoner i domstolene optimalisert for applikasjoner som skal kjøre i vår plattform, men minst mulig sikkerhetssårbarheter og mest mulig ferdigkonfigurert for deg.

## Bruk
Bruk dette imaget som base image i applikasjonen din og legg jar-filen under `/app/application.jar`:

```dockerfile
FROM ghcr.io/domstolene/jre:chiseled

# navn på tjenesten i sporingsoppsettet
ENV OTEL_SERVICE_NAME=ip-varsling-status

# WORKDIR er /app, slik at den havner i /app/application.jar
# spring-boot-konfigurasjon plukkes opp fra /config, /app/config og /deployments/config
ADD --chmod=644 --chown=0:1000 build/libs/*.jar application.jar
```

OBS: Ikke overstyr `WORKDIR`, det er satt til `/app` og application.jar havner da i `/app/application.jar`.
PS: Tross dette vil Spring Boot finne konfigurasjon i `/config`, `/app/config` og `/deployments/config`. Se `SPRING_CONFIG_ADDITIONAL_LOCATION` i [Dockerfile](Dockerfile) for detaljer.

### Standard tidssone

Imaget setter `TZ=Europe/Oslo`, slik at JVM-en bruker norsk tid med automatisk overgang mellom vintertid og sommertid. Tidssonen kan overstyres ved oppstart, for eksempel med `docker run --env TZ=UTC ...`, eller for JVM-en med `-Duser.timezone=UTC`.

### I pipeline

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
  --tty --stdin --profile=restricted \
  -- bash
```

MERKNADER: `oc debug` starter en debug-**pod**, så det fungerer ikke til å inspisere en kjørende applikasjon. `--profile=restricted` dropper Linux-rettigheten `SYS_PTRACE`, som utviklere ikke har tilgang på.

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
  --quiet --stdin --tty --profile=restricted \
  --image=eclipse-temurin:25-jdk \
  -- sh -c "cat /proc/1/environ | tr '\\0' '\\n'" \
  | grep DB_PASSWORD
```

Dersom du trenger `curl`, kan du bruke [Red Hat Universal Base Image](https://catalog.redhat.com/en/software/base-images):
```shell
kubectl debug --namespace $namespace $pod \
  --target=$app \
  --image=ubi10 \
  --tty --stdin --profile=restricted \
  -- bash
```


### Feilsøke base image
Siden det er så vanskelig å observere imaget uten verktøy i det, er det enkleste trikset å skrive
verktøy selv for å inspisere. Eksempelvis, dersom en ønsker å inspisere hva som er på PATH, kan
en bruke en instruks som denne:

> Lag et program i java 25 som finner alle program på PATH i linux. Det skal liste ut hele stien til programmet, slik som /bin/sh, filrettigheter og eier. Bruk enkel main-metode, uten klasse. Skriv til onpath.java

Kjør Java-filen direkte i containeren:
```fish
docker run --rm --mount "type=bind,src=$PWD,dst=/check,readonly" \
  ghcr.io/domstolene/jre:chiseled --source 25 /check/onpath.java
```

## Sikkerhet
Chiseled er et verktøy som [stripper Ubuntu helt ned](https://hub.docker.com/r/ubuntu/jre), slik at en kun har Java for å kjøre applikasjoner og ikke noe annet. Ingen pakker, ingen shell, ingenting annet enn det som trengs. Det medfører at vi ikke trenger bekymre oss for sårbarheter og angrep, fordi de oftes gjøres via flere steg:

1. Sårbarhet i applikasjonen.
2. Tilgjengelige verktøy, slik som `sh` eller `curl` i image.
3. Sårbarheter i tilgjengelige verktøy i image.

Når vi har fjernet steg 2, alle verktøy som finnes i image, så er det svært vanskelig å angripe en applikasjon.

Legg merke til at ubuntu/jre også inneholder kompilatoren `javac` og mange verktøy for feilsøking, slik som `jcmd`. De fjerner vi med [RemoveTools.java](src/RemoveTools.java), slik at en angriper ikke kan bruke de.

Tross at vi fjerner verktøyene, kan en fortsatt bruke Java-kompilatoren via `java --source 25 et-program.java`. Kompilatormodulen beholdes, så fjerning av `javac` hindrer ikke kompilering og kjøring av Java-kildekode. Dette er uavhengig av JVM-ens JIT-kompilering, som optimaliserer kode mens programmet kjører.

Dersom en trenger cli-verktøyene, se [seksjonen om feilsøking](#feilsøking) for hvordan en bruker verktøy som `jcmd` for å printe stacken.

### Fjerning av rotsertifikater vi ikke stoler på
[RemoveUntrustedCertificateRoots.java](src/RemoveUntrustedCertificateRoots.java) kjøres direkte med `java --source 25` under bygging for å fjerne rotsertifikater vi ikke stoler på fra `$JAVA_HOME/lib/security/cacerts`.
Dette beskytter mot de kompromitterte mellomsertifikatene fra [eMudhra-varslet](https://emudhra.we-have-a-problem.com/) ved å fjerne rotsertifikatene `emSign ECC Root CA - G3` (`emsigneccrootcag3 [jdk]`) og `AAA Certificate Services` (`comodoaaaca [jdk]`) fra `cacerts` (se Javadoc i [RemoveUntrustedCertificateRoots.java](src/RemoveUntrustedCertificateRoots.java) for begrunnelse og hvilke tjenester som potensielt påvirkes).
[Bygget i PR](.github/workflows/pr.yaml) feiler hvis rotsertifikatene fortsatt er betrodd via [CheckEmudhra.java](src/CheckEmudhra.java).

Kjør samme kontroll som i PR-workflowen lokalt:

```fish
docker run --rm \
  --mount "type=bind,src=$PWD/src,dst=/check,readonly" \
  ghcr.io/domstolene/jre --source 25 /check/CheckEmudhra.java --require-blocked
```

Forvent `TRUSTED=0 REJECTED=12 UNKNOWN=0`. Med `--require-blocked` kontrolleres det at de berørte rotsertifikatene er fjernet fra `cacerts` og at Java avviser sertifikatkjeden fordi det ikke finnes noen gyldig tillitskjede (`unable to find valid certification path to requested target`).
Nettverksfeil, utløpte sertifikater og annen avvisning gir ikke bestått kontroll.
Testen trenger DNS og utgående TCP/443 til testvertene. Den tester ikke JAR-signaturer
eller om tidligere angrep har funnet sted. Eksisterende tjenester må bygges på nytt
og rulles ut med det oppdaterte imaget.

## Workflows
[PR-workflowen](.github/workflows/pr.yaml) kjører på PR-er mot `main` og kan startes manuelt. Den bygger imaget for `linux/amd64` og kontrollerer at Java starter og sjekker at vi ikke stoler på utrygge sertifikater. Ingen publisering, signering, attestering, SBOM, scanning eller GitHub Pages.

[Publiseringsworkflowen](.github/workflows/build-and-publish.yml) kjører ved push til `main`, daglig og manuelt. Den bygger imaget for begge arkitekturer, publiserer, attesterer og signerer det, og kjører Syft og Grype før rapporten publiseres til GitHub Pages. Sertifikatkontrollen kjøres i PR-workflowen, ikke i publiseringsworkflowen.

## Sikkerhetsvarsler og scanning
Scanne med Red Hat Advanced Cluster Security:

```shell
export ROX_ENDPOINT=acs-ui.apps.ocp.mgmt.domstol.no:443
export ROX_API_TOKEN=<fås fra plattformteamet, se https://domstol.atlassian.net/browse/PLATTFORM-3192>
export GRPC_ENFORCE_ALPN_ENABLED=false # brannmuren vår støtter ikke ALPN
roxctl central login
roxctl image scan --output json --image ghcr.io/domstolene/jre:chiseled
```

Sjekke images som allerede er scannet:
```shell
roxctl image check --output json --image ghcr.io/domstolene/jre:chiseled
```

Andre verktøy som er verdt å nevne er [trivy](https://github.com/aquasecurity/trivy) og [syft](https://github.com/anchore/syft):

```shell
trivy image --severity HIGH,CRITICAL,MEDIUM ghcr.io/domstolene/jre:chiseled
syft ghcr.io/domstolene/jre:chiseled
```


## SLSA Provenance
SLSA provenance attesterer *hvordan* imaget ble bygget – hvilken workflow, fra hvilken commit, og av hvilken runner. Genereres automatisk av `actions/attest-build-provenance` i [workflow som bygger docker image](.github/workflows/build-and-publish.yml).

Verifiser attesteringen:
```shell
gh attestation verify oci://ghcr.io/domstolene/jre:latest \
  --repo domstolene/jre \
  --predicate-type https://slsa.dev/provenance/v1 # slsa er default predicate-type, men eksplisitt her for å skille med SBOM lenger ned
```

Se innholdet i provenance-predikatet:
```shell
gh attestation download oci://ghcr.io/domstolene/jre:latest \
  --repo domstolene/jre \
  --predicate-type https://slsa.dev/provenance/v1  \
  | grep "The trusted metadata is now available at sha256:" \
  | sed 's/.* sha256:/sha256:/' \
  | xargs jq '.dsseEnvelope.payload | @base64d | fromjson | .predicate'
  > slsa.json

# hvilken workflow som laget imaget
jq .buildDefinition.externalParameters.workflow slsa.json

# hvilken repo og commit imaget ble bygd fra
jq .buildDefinition.resolvedDependencies[] slsa.json

# hvilken run som gjorde bygget
jq .runDetails.metadata.invocationId slsa.json
```

Output inkluderer blant annet:

- `.buildDefinition.externalParameters.workflow` -
- `.buildDefinition.resolvedDependencies[].digest.gitCommit` - hvilken commit imaget ble bygd fra
- `.runDetails.builder.id` hvilken GitHub Actions runner som gjorde bygget


## SBOM
Software bill of materials, eller hva som er inni image, er generert fra [syft](https://github.com/anchore/syft) i [workflow som bygger docker image](.github/workflows/build-and-publish.yml).

SBOM kan observeres med:
```shell
gh attestation download oci://ghcr.io/domstolene/jre:chiseled --repo domstolene/jre \
  | grep "The trusted metadata is now available at sha256:" \
  | sed 's/.* sha256:/sha256:/' \
  | xargs jq --raw-output '.dsseEnvelope.payload | @base64d | fromjson | .predicate.components' \
  > components.json

jq .[].name components.json
```

Attestering av SBOM sjekkes med:
```shell
gh attestation verify oci://ghcr.io/domstolene/jre:latest \
  --repo domstolene/jre \
  --predicate-type https://cyclonedx.org/bom
```

Som skal gi et resultat tilsvarende dette:
```
Loaded digest sha256:70f1114bbf932bb448ff2762d9772391507498eeb34c70acae6e20e92908f0ed for oci://ghcr.io/domstolene/jre:latest
Loaded 1 attestation from GitHub API

The following policy criteria will be enforced:
- Predicate type must match:................ https://cyclonedx.org/bom
- Source Repository Owner URI must match:... https://github.com/domstolene
- Source Repository URI must match:......... https://github.com/domstolene/jre
- Subject Alternative Name must match regex: (?i)^https://github.com/domstolene/jre/
- OIDC Issuer must match:................... https://token.actions.githubusercontent.com

✓ Verification succeeded!

The following 1 attestation matched the policy criteria

- Attestation #1
  - Build repo:..... domstolene/jre
  - Build workflow:. .github/workflows/build-and-publish.yml@refs/heads/main
  - Signer repo:.... domstolene/jre
  - Signer workflow: .github/workflows/build-and-publish.yml@refs/heads/main
```

Legg merge til docker-manifest-digest, som du kan bruke til å pinne ned versjonen slik som dette: `ghcr.io/domstolene/jre@sha256:70f1114bbf932bb448ff2762d9772391507498eeb34c70acae6e20e92908f0ed`


## Signering
Signeringen som skjer i [workflow som bygger image](.github/workflows/build-and-publish.yml) garanterer at det var workflow på dette repoet som bygget imaget.

Signeringen kan sjekkes med `cosign`:

```shell
cosign verify ghcr.io/domstolene/jre:chiseled \
  --certificate-identity-regexp "https://github.com/domstolene/jre" \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  | jq .
```

Skal gi en output tilsvarende dette:
```
Verification for ghcr.io/domstolene/jre:chiseled --
The following checks were performed on each of these signatures:
  - The cosign claims were validated
  - Existence of the claims in the transparency log was verified offline
  - The code-signing certificate was verified using trusted certificate authority certificates
[
  {
    "critical": {
      "identity": {
        "docker-reference": "ghcr.io/domstolene/jre:chiseled"
      },
      "image": {
        "docker-manifest-digest": "sha256:70f1114bbf932bb448ff2762d9772391507498eeb34c70acae6e20e92908f0ed"
      },
      "type": "https://sigstore.dev/cosign/sign/v1"
    },
    "optional": {}
  }
]
```
