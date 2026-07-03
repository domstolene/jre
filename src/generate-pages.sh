#!/usr/bin/env bash
# Genererer digest.svg og index.html til OUTPUT_DIR.
# Brukes fra GitHub Actions workflow – skal ikke kopieres inn i Docker-image.
#
# Påkrevde miljøvariabler:
#   DIGEST     – f.eks. sha256:6e154e04...
#   IMAGE      – f.eks. ghcr.io/domstolene/jre
#   OUTPUT_DIR – f.eks. ./dist (default: ./dist)
#
# BASE_IMAGE, JAVA_VERSION og UBUNTU_VERSION leses automatisk fra Dockerfile.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DOCKERFILE="${SCRIPT_DIR}/../Dockerfile"

# Les base image fra linjen «FROM docker.io/ubuntu/jre:TAG AS chiseled»
BASE_IMAGE=$(grep -m1 'FROM docker.io/ubuntu/jre' "$DOCKERFILE" | awk '{print $2}')
TAG="${BASE_IMAGE#*:}"                        # f.eks. 25-26.04_edge
JAVA_VERSION="${TAG%%-*}"                     # f.eks. 25
UBUNTU_VERSION="$(echo "$TAG" | cut -d'-' -f2)" # f.eks. 26.04

OUTPUT_DIR="${OUTPUT_DIR:-./dist}"
mkdir -p "$OUTPUT_DIR"

IMAGE=${IMAGE:-"ghcr.io/domstolene/jre"}
DIGEST=${DIGEST:?"DIGEST environment variable is required, example: DIGEST=sha256:6e154e0440805d408cd556a2ff4a4c8a217ff5d1bb8ad6e7ca517d5d14a6aae $0"}

FULL_REF="${IMAGE}@${DIGEST}"
BUILD_TIME=$(date -u '+%Y-%m-%dT%H:%M UTC')

# ---------------------------------------------------------------------------
# SVG-badge – viser kun sha256-digest (ikke image-navn)
# Monospace 12px ≈ 7.2 px/tegn; "sha256:" (7) + 64 hex = 71 tegn ≈ 511 px
# Label-felt: 90 px → total bredde: 90 + 511 + 19 padding = 620 px
# ---------------------------------------------------------------------------
cat > "${OUTPUT_DIR}/digest.svg" << EOF
<svg xmlns="http://www.w3.org/2000/svg" width="620" height="30">
  <rect width="620" height="30" rx="3" fill="#555"/>
  <rect x="90" width="530" height="30" rx="3" fill="#007ec6"/>
  <text x="8" y="20" fill="#fff" font-family="monospace" font-size="12" font-weight="bold">Siste bygg:</text>
  <text x="98" y="20" fill="#fff" font-family="monospace" font-size="12">${DIGEST}</text>
</svg>
EOF

# ---------------------------------------------------------------------------
# index.html – tabell med detaljer om siste image
# ---------------------------------------------------------------------------
cat > "${OUTPUT_DIR}/index.html" << EOF
<!DOCTYPE html>
<html lang="no">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>JRE Docker Image – siste bygg</title>
  <style>
    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
      font-size: 14px;
      color: #24292f;
      background: #ffffff;
      max-width: 1100px;
      margin: 2.5rem auto;
      padding: 0 1.25rem;
    }
    h1 { font-size: 1.5rem; border-bottom: 1px solid #d0d7de; padding-bottom: .6rem; margin-bottom: .75rem; }
    .meta { color: #57606a; margin-bottom: 1.75rem; font-size: .9rem; }
    table { width: 100%; border-collapse: collapse; border: 1px solid #d0d7de; border-radius: 6px; overflow: hidden; }
    thead th {
      background: #0969da;
      color: #ffffff;
      text-align: left;
      padding: .65rem 1rem;
      font-size: .8rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: .06em;
    }
    tbody tr:nth-child(odd)  td { background: #ffffff; }
    tbody tr:nth-child(even) td { background: #f6f8fa; }
    td { padding: .65rem 1rem; vertical-align: middle; border-top: 1px solid #d0d7de; }
    td:first-child { width: 200px; color: #57606a; font-weight: 600; white-space: nowrap; }
    code {
      font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
      font-size: .85em;
      background: #eaeef2;
      padding: .15em .4em;
      border-radius: 4px;
      word-break: break-all;
    }
    .copy-btn {
      margin-left: .5rem;
      padding: .2rem .55rem;
      font-size: .75rem;
      cursor: pointer;
      border: 1px solid #d0d7de;
      border-radius: 4px;
      background: #f6f8fa;
      color: #24292f;
      vertical-align: middle;
      transition: background .15s, color .15s, border-color .15s;
    }
    .copy-btn:hover { background: #0969da; color: #fff; border-color: #0969da; }
    .badge { margin-top: 2rem; }
  </style>
</head>
<body>
  <h1>JRE Docker Image – siste bygg</h1>
  <p class="meta">Bygget: <strong>${BUILD_TIME}</strong></p>

  <table>
    <thead>
      <tr>
        <th>Egenskap</th>
        <th>Verdi</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td>Full image-referanse</td>
        <td>
          <code id="full-ref">${FULL_REF}</code>
          <button class="copy-btn" onclick="navigator.clipboard.writeText(document.getElementById('full-ref').textContent)">Kopier</button>
        </td>
      </tr>
      <tr>
        <td>SHA256-digest</td>
        <td>
          <code id="digest">${DIGEST}</code>
          <button class="copy-btn" onclick="navigator.clipboard.writeText(document.getElementById('digest').textContent)">Kopier</button>
        </td>
      </tr>
      <tr>
        <td>Image-navn</td>
        <td><code>${IMAGE}</code></td>
      </tr>
      <tr>
        <td>Java-versjon</td>
        <td><code>${JAVA_VERSION}</code></td>
      </tr>
      <tr>
        <td>Ubuntu-versjon</td>
        <td><code>${UBUNTU_VERSION}</code></td>
      </tr>
      <tr>
        <td>Base image</td>
        <td><code>${BASE_IMAGE}</code></td>
      </tr>
    </tbody>
  </table>

  <div class="badge">
    <img src="digest.svg" alt="SHA256-digest for siste bygg">
  </div>
</body>
</html>
EOF

echo "Genererte ${OUTPUT_DIR}/digest.svg og ${OUTPUT_DIR}/index.html"
