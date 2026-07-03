#!/usr/bin/env bash
set -xeo pipefail

mkdir -p "$JAVA_HOME"

TARGETARCH=${1:-$(uname -m)}
JAVA_VERSION=${2:-25}

# 1. Translate Docker's architecture names to Adoptium's API terms
if [[ "$TARGETARCH" == "amd64" ]]; then
    ADOPTIUM_ARCH="x64";
elif [[ "$TARGETARCH" == "arm64" ]]; then
    ADOPTIUM_ARCH="aarch64";
else
    echo "Unsupported architecture: $TARGETARCH"
    exit 1;
fi

# 2. Build the dynamic API URL using the translated architecture
API_URL="https://api.adoptium.net/v3/binary/latest/${JAVA_VERSION}/ga/linux/${ADOPTIUM_ARCH}/jre/hotspot/normal/eclipse"
METADATA_URL="https://api.adoptium.net/v3/assets/feature_releases/${JAVA_VERSION}/ga?architecture=${ADOPTIUM_ARCH}&image_type=jre&os=linux&project=jdk"

# 3. Download, verify, and extract
echo "⏳ Downloading Temurin JRE ${JAVA_VERSION} for ${ADOPTIUM_ARCH}..."
curl --location "${API_URL}" -o /tmp/openjdk.tar.gz
echo "✅ Downloading Temurin JRE ${JAVA_VERSION} for ${ADOPTIUM_ARCH}..."

echo "⏳ Verifying checksum..."
# Mulig dårlig antagelse her, første checksum = den vi lastet ned.
EXPECTED_SHA=$(curl --silent --location "${METADATA_URL}" | grep '"checksum":' | head -n 1 | cut -d'"' -f4)
echo "${EXPECTED_SHA}  /tmp/openjdk.tar.gz" | sha256sum -c -
echo "✅ Checksum verified."

echo "⏳ Extracting JRE..."
tar -xzf /tmp/openjdk.tar.gz -C "$JAVA_HOME" --strip-components=1
rm /tmp/openjdk.tar.gz
echo "✅ JRE extracted."
