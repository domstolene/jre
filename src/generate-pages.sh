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
UBUNTU_VERSION="$(echo "$TAG" | cut -d'-' -f2)" # f.eks. 26.04

OUTPUT_DIR="${OUTPUT_DIR:-./dist}"
SYFT_JSON="${SYFT_JSON:-${SCRIPT_DIR}/../syft.json}"
GRYPE_JSON="${GRYPE_JSON:-${SCRIPT_DIR}/../grype.json}"
mkdir -p "$OUTPUT_DIR"

IMAGE=${IMAGE:-"ghcr.io/domstolene/jre"}
DIGEST=${DIGEST:?"DIGEST environment variable is required, example: DIGEST=sha256:502a7e0e996bdfe2ff6fdc6204350f016af1ca04df69ae315dc8fdd310c502aa $0"}
JAVA_VERSION=$(docker run "$IMAGE@$DIGEST" -version 2>&1 | grep -v JAVA_TOOL_OPTIONS)

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
      var _syft, _grype;

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
        var cols = ['CVE', 'Alvorlighetsgrad', 'Pakke', 'Versjon', 'Fikset i'];
        var rows = sorted.map(function (m) {
          var fix = (m.vulnerability.fix && m.vulnerability.fix.versions && m.vulnerability.fix.versions.length)
            ? m.vulnerability.fix.versions.join(', ')
            : '\u2014';
          return [
            m.vulnerability.id || '',
            m.vulnerability.severity || '',
            m.artifact.name || '',
            m.artifact.version || '',
            fix
          ];
        });
        el.innerHTML = buildTable(cols, rows);
        addSort(el.querySelector('table'));
      }

      window.showJson = function (type) {
        var data  = type === 'syft' ? _syft : _grype;
        var title = type === 'syft' ? 'syft.json' : 'grype.json';
        document.getElementById('json-dialog-title').textContent = title;
        document.getElementById('json-content').textContent = JSON.stringify(data, null, 2);
        document.getElementById('json-dialog').showModal();
      };

      document.getElementById('json-dialog').addEventListener('click', function (e) {
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
