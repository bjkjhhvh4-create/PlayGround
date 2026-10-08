#!/usr/bin/env bash
# Once-per-worker prerequisites for start.sh / capture.sh (idempotent, fast).
set -euo pipefail
for bin in python3 node curl timeout playwright-cli chromium; do
  command -v "$bin" >/dev/null || { echo "missing prerequisite: $bin" >&2; exit 1; }
done
/usr/bin/time -p python3 -c 'import json; print("python json ok")'
/usr/bin/time -p chromium --version
echo 'startup prerequisites ok'
