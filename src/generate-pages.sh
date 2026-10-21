#!/usr/bin/env bash
# Genererer digest.svg og index.html til OUTPUT_DIR.
# Brukes fra GitHub Actions workflow – skal ikke kopieres inn i Docker-image.
#
# Påkrevde miljøvariabler:
#   DIGEST     – f.eks. sha256:6e154e04...
#   IMAGE      – f.eks. ghcr.io/domstolene/jre
#   OUTPUT_DIR – f.eks. ./dist (default: ./dist)
#
# BASE_IMAGE og UBUNTU_VERSION leses automatisk fra Dockerfile.
# JAVA_VERSION leses fra imaget, og DA_OTEL_AGENT_VERSION fra
# /da-otel-agent/version-info-<versjon> i imaget, uten shell i containeren.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DOCKERFILE="${SCRIPT_DIR}/../Dockerfile"

# Les base image fra linjen «FROM docker.io/ubuntu/jre:TAG AS chiseled»
BASE_IMAGE=$(grep -m1 'FROM docker.io/ubuntu/jre' "$DOCKERFILE" | awk '{print $2}')
TAG="${BASE_IMAGE#*:}"                        # f.eks. 25-26.04_edge
UBUNTU_VERSION="$(echo "$TAG" | cut -d'-' -f2)" # f.eks. 26.04

OUTPUT_DIR="${OUTPUT_DIR:-./dist}"
SYFT_JSON="${SYFT_JSON:-${SCRIPT_DIR}/../syft.json}"
GRYPE_JSON="${GRYPE_JSON:-${SCRIPT_DIR}/../grype.json}"
mkdir -p "$OUTPUT_DIR"

IMAGE=${IMAGE:-"ghcr.io/domstolene/jre"}
DIGEST=${DIGEST:?"DIGEST environment variable is required, example: DIGEST=sha256:502a7e0e996bdfe2ff6fdc6204350f016af1ca04df69ae315dc8fdd310c502aa $0"}
JAVA_VERSION=$(docker run "$IMAGE@$DIGEST" -version 2>&1 | grep -v JAVA_TOOL_OPTIONS)

VERSION_CONTAINER=$(docker create "$IMAGE@$DIGEST")
trap 'docker rm "$VERSION_CONTAINER" > /dev/null' EXIT
DA_OTEL_AGENT_VERSION=$(docker cp "${VERSION_CONTAINER}:/da-otel-agent/." - \
  | tar -tf - \
  | awk -F/ '$NF ~ /^version-info-/ {sub(/^version-info-/, "", $NF); print $NF}')
if [[ ! "$DA_OTEL_AGENT_VERSION" =~ ^[[:alnum:]][[:alnum:].+_-]*$ ]]; then
  echo "Forventet én versjonsfil /da-otel-agent/version-info-<versjon> i $IMAGE@$DIGEST, fant: '$DA_OTEL_AGENT_VERSION'" >&2
  exit 1
fi
docker rm "$VERSION_CONTAINER" > /dev/null
trap - EXIT

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
      display: inline-block;
      font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
      font-size: .85em;
      background: #eaeef2;
      padding: .15em .4em;
      border-radius: 4px;
      white-space: pre-wrap;
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
    section { margin-top: 2.5rem; }
    section h2 { font-size: 1.2rem; font-weight: 600; border-bottom: 1px solid #d0d7de; padding-bottom: .4rem; margin-bottom: .75rem; }
    .log-output pre {
      background: #0d1117;
      color: #c9d1d9;
      font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
      font-size: .8rem;
      line-height: 1.6;
      padding: .75rem 1rem;
      border-radius: 6px;
      margin-bottom: .75rem;
      white-space: pre-wrap;
      word-break: break-all;
    }
    .json-btn {
      margin-bottom: .75rem;
      padding: .3rem .75rem;
      font-size: .8rem;
      cursor: pointer;
      border: 1px solid #d0d7de;
      border-radius: 4px;
      background: #f6f8fa;
      color: #24292f;
      transition: background .15s, color .15s, border-color .15s;
    }
    .json-btn:hover { background: #0969da; color: #fff; border-color: #0969da; }
    .search-field {
      display: block;
      width: 100%;
      max-width: 400px;
      padding: .4rem .75rem;
      font-size: .85rem;
      border: 1px solid #d0d7de;
      border-radius: 4px;
      margin-bottom: .75rem;
      color: #24292f;
      background: #fff;
    }
    .search-field:focus { border-color: #0969da; box-shadow: 0 0 0 3px rgba(9,105,218,.1); outline: none; }
    thead th { cursor: pointer; user-select: none; }
    thead th:hover { background: #0550ae; }
    thead th[data-sort="asc"]::after  { content: " \25b2"; font-size: .7em; opacity: .7; }
    thead th[data-sort="desc"]::after { content: " \25bc"; font-size: .7em; opacity: .7; }
    .no-vulns { color: #1a7f37; font-weight: 600; padding: .5rem 0; margin: 0; }
    tbody tr.cve-row { cursor: pointer; }
    tbody tr.cve-row:hover td { background: #dbeafe !important; }
    #cve-dialog {
      position: fixed;
      inset: 0;
      width: 660px;
      max-width: 95vw;
      max-height: 85vh;
      margin: auto;
      border: 1px solid #d0d7de;
      border-radius: 8px;
      padding: 1.5rem;
      overflow-y: auto;
      box-shadow: 0 8px 24px rgba(0,0,0,.25);
    }
    #cve-dialog::backdrop { background: rgba(0,0,0,.5); }
    .h-table { width: 100%; border-collapse: collapse; border: 1px solid #d0d7de; border-radius: 6px; overflow: hidden; margin-bottom: .5rem; }
    .h-table tr:nth-child(odd)  th,
    .h-table tr:nth-child(odd)  td { background: #ffffff; }
    .h-table tr:nth-child(even) th,
    .h-table tr:nth-child(even) td { background: #f6f8fa; }
    .h-table th { width: 150px; text-align: left; padding: .55rem .85rem; font-weight: 600; color: #57606a; border-top: 1px solid #d0d7de; white-space: nowrap; vertical-align: top; cursor: default; }
    .h-table td { padding: .55rem .85rem; border-top: 1px solid #d0d7de; vertical-align: top; word-break: break-word; }
    .h-table td a { color: #0969da; }
    .h-table tr:first-child th, .h-table tr:first-child td { border-top: none; }
    .cve-desc { margin-top: 1rem; padding-top: 1rem; border-top: 1px solid #d0d7de; font-size: .9em; line-height: 1.6; }
    #json-dialog {
      position: fixed;
      inset: 0;
      width: 90vw;
      height: 85vh;
      max-height: 85vh;
      margin: auto;
      border: 1px solid #d0d7de;
      border-radius: 8px;
      padding: 1.25rem;
      overflow: hidden;
      box-shadow: 0 8px 24px rgba(0,0,0,.25);
    }
    #json-dialog[open] { display: flex; flex-direction: column; }
    #json-dialog::backdrop { background: rgba(0,0,0,.5); }
    .dialog-header {
      display: flex;
      align-items: center;
      gap: .5rem;
      margin-bottom: .75rem;
      flex-shrink: 0;
    }
    .dialog-header strong { flex: 1; font-family: ui-monospace, monospace; font-size: .9rem; }
    #json-content {
      flex: 1;
      overflow: auto;
      font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
      font-size: .78rem;
      background: #f6f8fa;
      padding: .75rem;
      border-radius: 4px;
      margin: 0;
      white-space: pre;
    }
  </style>
</head>
<body>
  <h1>JRE Docker Image – siste bygg</h1>
  <p class="meta">Bygget: <strong>${BUILD_TIME}</strong></p>
  <p class="meta">Repo: <a href="https://github.com/domstolene/jre">domstolene/jre</a></p>

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
        <td>da-otel-agent-versjon</td>
        <td><code>${DA_OTEL_AGENT_VERSION}</code></td>
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

  <section>
    <h2>S&#229;rbarheter i image</h2>
    <div id="grype-log" class="log-output"><pre>Laster&#8230;</pre></div>
    <button class="json-btn" onclick="showJson('grype')">Vis JSON</button>
    <div id="grype-table"></div>
  </section>

  <section>
    <h2>Innhold i image</h2>
    <div id="syft-log" class="log-output"><pre>Laster&#8230;</pre></div>
    <button class="json-btn" onclick="showJson('syft')">Vis JSON</button>
    <input id="syft-search" class="search-field" type="search" placeholder="S&#248;k i pakkeliste&#8230;">
    <div id="syft-table"></div>
  </section>

  <dialog id="cve-dialog">
    <div class="dialog-header">
      <strong id="cve-dialog-title" style="font-family:ui-monospace,monospace;font-size:.95rem"></strong>
      <button class="copy-btn" onclick="document.getElementById('cve-dialog').close()">Lukk &#x2715;</button>
    </div>
    <dl class="h-table" id="cve-dl"></dl>
    <div class="cve-desc" id="cve-desc"></div>
  </dialog>

  <dialog id="json-dialog">
    <div class="dialog-header">
      <strong id="json-dialog-title"></strong>
      <button class="copy-btn" onclick="navigator.clipboard.writeText(document.getElementById('json-content').textContent)">Kopier</button>
      <button class="copy-btn" onclick="document.getElementById('json-dialog').close()">Lukk &#x2715;</button>
    </div>
    <pre id="json-content"></pre>
  </dialog>

  <script>
    (function () {
      var _syft, _grype, _cveMatches;

      function esc(s) {
        return String(s == null ? '' : s)
          .replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;');
      }

      function buildTable(cols, rows) {
        var ths = cols.map(function (c) { return '<th>' + esc(c) + '</th>'; }).join('');
        var trs = rows.map(function (r) {
          return '<tr>' + r.map(function (c) { return '<td>' + esc(c) + '</td>'; }).join('') + '</tr>';
        }).join('');
        return '<table><thead><tr>' + ths + '</tr></thead><tbody>' + trs + '</tbody></table>';
      }

      function filterTable(term, tbl) {
        var q = term.toLowerCase();
        tbl.querySelectorAll('tbody tr').forEach(function (row) {
          var text = Array.from(row.cells).map(function (c) { return c.textContent; }).join('\t').toLowerCase();
          row.hidden = q.length > 0 && text.indexOf(q) === -1;
        });
      }

      function addSort(tbl) {
        tbl.querySelectorAll('th').forEach(function (th, col) {
          var asc = true;
          th.addEventListener('click', function () {
            var tbody = tbl.querySelector('tbody');
            var rows = Array.from(tbody.querySelectorAll('tr'));
            rows.sort(function (a, b) {
              var x = a.cells[col].textContent;
              var y = b.cells[col].textContent;
              return asc ? x.localeCompare(y, 'nb') : y.localeCompare(x, 'nb');
            });
            asc = !asc;
            tbl.querySelectorAll('th').forEach(function (t) { t.removeAttribute('data-sort'); });
            th.dataset.sort = asc ? 'desc' : 'asc';
            rows.forEach(function (r) { tbody.appendChild(r); });
          });
        });
      }

      function renderSyft(data) {
        var pkgs = data.artifacts || [];
        var byType = {};
        pkgs.forEach(function (a) { byType[a.type] = (byType[a.type] || 0) + 1; });
        var typeStr = Object.keys(byType)
          .sort(function (a, b) { return byType[b] - byType[a]; })
          .map(function (t) { return byType[t] + ' x ' + t; })
          .join(', ');
        var tgt = data.source && (data.source.metadata || data.source.target);
        var src = (tgt && (tgt.userInput || tgt.image || tgt.path)) || (data.source && data.source.name) || 'ukjent';
        var ts  = (data.descriptor && data.descriptor.timestamp)
               || (tgt && tgt.labels && tgt.labels['org.opencontainers.image.created'])
               || '';
        var ver = (data.descriptor && data.descriptor.version)   || '';
        var nm  = (data.descriptor && data.descriptor.name)      || 'syft';

        document.getElementById('syft-log').innerHTML =
          '<pre>' +
          esc(nm) + ' ' + esc(ver) + '\n' +
          'Scannet:   ' + esc(src) + '\n' +
          'Tidspunkt: ' + esc(ts) + '\n' +
          'Pakker:    ' + pkgs.length + ' (' + esc(typeStr) + ')' +
          '</pre>';

        var cols = ['Navn', 'Versjon', 'Type', 'Plassering'];
        var rows = pkgs.map(function (a) {
          return [
            a.name || '',
            a.version || '',
            a.type || '',
            (a.locations && a.locations[0] && a.locations[0].path) || ''
          ];
        });
        var el = document.getElementById('syft-table');
        el.innerHTML = buildTable(cols, rows);
        addSort(el.querySelector('table'));
        var searchEl = document.getElementById('syft-search');
        searchEl.value = '';
        searchEl.addEventListener('input', function () {
          filterTable(this.value, el.querySelector('table'));
        });
      }

      function renderGrype(data) {
        var matches = data.matches || [];
        var src  = (data.source && data.source.target && data.source.target.userInput) || 'ukjent';
        var ts   = (data.descriptor && data.descriptor.timestamp) || '';
        var ver  = (data.descriptor && data.descriptor.version)   || '';
        var nm   = (data.descriptor && data.descriptor.name)      || 'grype';
        var dbSt = data.descriptor && data.descriptor.db && data.descriptor.db.status;
        var dbBuilt = (dbSt && dbSt.built) || '';

        document.getElementById('grype-log').innerHTML =
          '<pre>' +
          esc(nm) + ' ' + esc(ver) + '\n' +
          'Scannet:      ' + esc(src) + '\n' +
          'Tidspunkt:    ' + esc(ts) + '\n' +
          'S\u00e5rbarheter: ' + matches.length + '\n' +
          'DB bygget:    ' + esc(dbBuilt) +
          '</pre>';

        var el = document.getElementById('grype-table');
        if (!matches.length) {
          el.innerHTML = '<p class="no-vulns">Ingen kjente s\u00e5rbarheter funnet</p>';
          return;
        }
        var sevOrd = { Critical: 0, High: 1, Medium: 2, Low: 3, Negligible: 4, Unknown: 5 };
        var sorted = matches.slice().sort(function (a, b) {
          var sa = sevOrd[a.vulnerability.severity] !== undefined ? sevOrd[a.vulnerability.severity] : 9;
          var sb = sevOrd[b.vulnerability.severity] !== undefined ? sevOrd[b.vulnerability.severity] : 9;
          return sa - sb;
        });
        _cveMatches = sorted;
        var colDefs = [
          { label: 'CVE', tip: null },
          { label: 'Alvorlighet', tip: null },
          { label: 'Pakke', tip: null },
          { label: 'Beskrivelse', tip: null }
        ];
        var ths = colDefs.map(function (c) {
          return c.tip
            ? '<th title="' + esc(c.tip) + '" style="cursor:help">' + esc(c.label) + ' \u24d8</th>'
            : '<th>' + esc(c.label) + '</th>';
        }).join('');
        var trs = sorted.map(function (m, idx) {
          var id = m.vulnerability.id || '';
          var desc = (m.relatedVulnerabilities || []).reduce(function (s, rv) { return s || rv.description || ''; }, '') || m.vulnerability.description || '';
          return '<tr class="cve-row" onclick="showCveDetail(' + idx + ')">' +
            '<td><a href="https://nvd.nist.gov/vuln/detail/' + esc(id) + '" target="_blank" rel="noopener noreferrer" onclick="event.stopPropagation()">' + esc(id) + '</a></td>' +
            '<td>' + esc(m.vulnerability.severity || '') + '</td>' +
            '<td>' + esc(m.artifact.name || '') + '</td>' +
            '<td style="font-size:.9em">' + esc(desc) + '</td>' +
            '</tr>';
        }).join('');
        el.innerHTML = '<table><thead><tr>' + ths + '</tr></thead><tbody>' + trs + '</tbody></table>';
        addSort(el.querySelector('table'));
      }

      window.showJson = function (type) {
        var data  = type === 'syft' ? _syft : _grype;
        var title = type === 'syft' ? 'syft.json' : 'grype.json';
        document.getElementById('json-dialog-title').textContent = title;
        document.getElementById('json-content').textContent = JSON.stringify(data, null, 2);
        document.getElementById('json-dialog').showModal();
      };

      window.showCveDetail = function (idx) {
        var m = _cveMatches[idx];
        var id = m.vulnerability.id || '';
        var epssArr = m.vulnerability.epss;
        var epssVal = (epssArr && epssArr.length) ? (epssArr[0].epss * 100).toFixed(3) + '\u00a0%' : '\u2014';
        var pctVal  = (epssArr && epssArr.length) ? (epssArr[0].percentile * 100).toFixed(1) + '\u00a0%' : '\u2014';
        var fix = (m.vulnerability.fix && m.vulnerability.fix.versions && m.vulnerability.fix.versions.length)
          ? m.vulnerability.fix.versions.join(', ')
          : '\u2014';
        var allCvss = (m.vulnerability.cvss || []).concat(
          (m.relatedVulnerabilities || []).reduce(function (acc, rv) { return acc.concat(rv.cvss || []); }, [])
        ).filter(function (c, i, arr) {
          return arr.findIndex(function (x) { return x.source === c.source; }) === i;
        });
        var cvssHtml = allCvss.length
          ? '<table style="width:100%;border-collapse:collapse;border:1px solid #d0d7de;border-radius:4px;overflow:hidden;font-size:.85em;margin-top:.25rem">' +
            '<thead><tr>' +
            '<th style="background:#0969da;color:#fff;text-align:left;padding:.4rem .75rem;font-weight:600">Kilde</th>' +
            '<th style="background:#0969da;color:#fff;text-align:left;padding:.4rem .75rem;font-weight:600" title="CVSS Base Score (0\u201310). Under 4: lav. 4\u20136.9: medium. 7\u20138.9: h\u00f8y. 9\u201310: kritisk.">Score \u24d8</th>' +
            '<th style="background:#0969da;color:#fff;text-align:left;padding:.4rem .75rem;font-weight:600" title="Utnyttbarhet (0\u20133.9): hvor lett s\u00e5rbarheten er \u00e5 utnytte. F.eks. 3.9 = ingen autentisering, tilgjengelig over nett; 2.0 = krever lokal tilgang; 0.5 = krever komplekse betingelser og brukerinteraksjon.">Utnytt. \u24d8</th>' +
            '<th style="background:#0969da;color:#fff;text-align:left;padding:.4rem .75rem;font-weight:600" title="P\u00e5virkning (0\u20136): konsekvensene ved utnyttelse. F.eks. 5.9 = full tap av konfidensialitet, integritet eller tilgjengelighet; 3.6 = delvis p\u00e5virkning p\u00e5 \u00e9n egenskap; 1.4 = minimal skade uten varig p\u00e5virkning.">P\u00e5virk. \u24d8</th>' +
            '</tr></thead><tbody>' +
            allCvss.map(function (c, i) {
              var bg = i % 2 === 0 ? '#ffffff' : '#f6f8fa';
              var badge = c.type === 'Primary'
                ? ' <span style="font-size:.7em;background:#0969da;color:#fff;border-radius:3px;padding:.1em .35em;vertical-align:middle">Primary</span>'
                : '';
              var b  = c.metrics && c.metrics.baseScore           != null ? c.metrics.baseScore.toFixed(1)           : '\u2014';
              var ex = c.metrics && c.metrics.exploitabilityScore != null ? c.metrics.exploitabilityScore.toFixed(1) : '\u2014';
              var im = c.metrics && c.metrics.impactScore         != null ? c.metrics.impactScore.toFixed(1)         : '\u2014';
              var rawVector = c.vector ? c.vector.replace(/^CVSS:\d+\.\d+\//, '') : '';
              var calcUrl = 'https://nvd.nist.gov/vuln-metrics/cvss/v3-calculator?vector=' + encodeURIComponent(rawVector) + '&version=' + encodeURIComponent(c.version);
              var td = 'style="padding:.4rem .75rem;border-top:1px solid #d0d7de;background:' + bg + ';vertical-align:top"';
              return '<tr style="cursor:pointer" onclick="window.open(\'' + esc(calcUrl) + '\',\'_blank\')" title="\\u00c5pne i CVSS-kalkulator p\\u00e5 nvd.nist.gov">' +
                '<td ' + td + '>' + esc(c.source) + badge + '</td>' +
                '<td ' + td + '>' + esc(b)  + '</td>' +
                '<td ' + td + '>' + esc(ex) + '</td>' +
                '<td ' + td + '>' + esc(im) + '</td>' +
                '</tr>';
            }).join('') +
            '</tbody></table>'
          : '\u2014';
        var allUrls = (m.vulnerability.urls || []).concat(
          (m.relatedVulnerabilities || []).reduce(function (acc, rv) { return acc.concat(rv.urls || []); }, [])
        ).filter(function (u, i, arr) { return arr.indexOf(u) === i; });
        var urlsHtml = allUrls.length
          ? allUrls.map(function (u) {
              var label = u.replace(/^https?:\/\//, '').replace(/\/$/, '');
              return '<a href="' + esc(u) + '" target="_blank" rel="noopener noreferrer">' + esc(label) + '</a>';
            }).join('<br>')
          : '\u2014';
        var desc = (m.relatedVulnerabilities || []).reduce(function (s, rv) { return s || rv.description || ''; }, '') || m.vulnerability.description || '';
        document.getElementById('cve-dialog-title').textContent = id;
        document.getElementById('cve-dl').outerHTML = '<table class="h-table" id="cve-dl"><tbody>' +
          '<tr><th>Alvorlighet</th><td>' + esc(m.vulnerability.severity || '\u2014') + '</td></tr>' +
          '<tr><th>Pakke</th><td>' + esc(m.artifact.name || '') + '\u00a0' + esc(m.artifact.version || '') + '</td></tr>' +
          '<tr><th>Fikset i</th><td>' + esc(fix) + '</td></tr>' +
          '<tr><th title="Common Vulnerability Scoring System \u2013 tallfestet alvorlighetsgrad 0\u201310. Under 4: lav. 4\u20136.9: medium. 7\u20138.9: h\u00f8y. 9\u201310: kritisk. Utnyttbarhet (0\u20133.9) m\u00e5ler hvor lett angrepet er. P\u00e5virkning (0\u20136) m\u00e5ler konsekvensene.">CVSS per kilde \u24d8</th><td>' + cvssHtml + '</td></tr>' +
          '<tr><th title="Exploit Prediction Scoring System \u2013 sannsynligheten for at s\u00e5rbarheten aktivt utnyttes innen 30 dager. F.eks. betyr 2.0\u00a0% at det er 2 av 100 sjanse for at s\u00e5rbarheten aktivt utnyttes i l\u00f8pet av neste m\u00e5ned.">EPSS \u24d8</th><td>' + esc(epssVal) + '</td></tr>' +
          '<tr><th title="Rangerer s\u00e5rbarheten etter EPSS-score mot alle andre kjente CVE-er. 90\u00a0% betyr at kun 10\u00a0% av alle CVE-er har h\u00f8yere sannsynlighet for utnyttelse \u2013 alvorlig. 50\u00a0% betyr midt p\u00e5 treet. 10\u00a0% betyr at 90\u00a0% har h\u00f8yere score \u2013 lav risiko.">Persentil \u24d8</th><td>' + esc(pctVal) + '</td></tr>' +
          '<tr><th>Lenker</th><td style="line-height:1.8">' + urlsHtml + '</td></tr>' +
          '</tbody></table>';
        document.getElementById('cve-desc').innerHTML = desc
          ? '<strong>Beskrivelse</strong><p style="margin:.5rem 0 0">' + esc(desc) + '</p>' : '';
        document.getElementById('cve-dialog').showModal();
      };

      document.getElementById('json-dialog').addEventListener('click', function (e) {
        if (e.target === this) this.close();
      });

      document.getElementById('cve-dialog').addEventListener('click', function (e) {
        if (e.target === this) this.close();
      });

      Promise.all([
        fetch('syft.json').then(function (r) { return r.json(); }),
        fetch('grype.json').then(function (r) { return r.json(); })
      ]).then(function (res) {
        _syft  = res[0];
        _grype = res[1];
        renderSyft(_syft);
        renderGrype(_grype);
      }).catch(function (err) {
        ['syft-log', 'grype-log'].forEach(function (id) {
          document.getElementById(id).innerHTML = '<pre>Feil ved lasting: ' + esc(String(err)) + '</pre>';
        });
      });
    }());
  </script>
</body>
</html>
EOF

cp "$SYFT_JSON" "${OUTPUT_DIR}/syft.json"
cp "$GRYPE_JSON" "${OUTPUT_DIR}/grype.json"

echo "Genererte ${OUTPUT_DIR}/digest.svg, ${OUTPUT_DIR}/index.html, ${OUTPUT_DIR}/syft.json og ${OUTPUT_DIR}/grype.json"
