#!/usr/bin/env bash
# Serve the built static site (dist/) in the foreground on $PORT (default 3000).
# Writes worker metadata (deployment-output.json) to $OPENCODE_WEB_DIR.
set -euo pipefail
cd "$(dirname "$0")"
PROJECT_DIR="$PWD"
: "${PORT:=3000}"
: "${OPENCODE_WEB_DIR:=/home/runner/work/_temp/omgithub-web}"
DIST="$PROJECT_DIR/dist"

# Install dependencies when the project declares any.
if /usr/bin/time -p test -f "$PROJECT_DIR/package.json"; then
  if /usr/bin/time -p test -f "$PROJECT_DIR/package-lock.json"; then
    /usr/bin/time -p npm ci --no-audit --no-fund --prefix "$PROJECT_DIR"
  else
    /usr/bin/time -p npm install --no-audit --no-fund --prefix "$PROJECT_DIR"
  fi
fi

# Build when needed: the static output must contain index.html.
if ! /usr/bin/time -p test -f "$DIST/index.html"; then
  echo "Static deployment output must contain dist/index.html" >&2
  exit 1
fi
# Keep the downloadable APK in the built output fresh.
if /usr/bin/time -p test -f "$PROJECT_DIR/game/game-noor-oasis.apk"; then
  /usr/bin/time -p cp -u "$PROJECT_DIR/game/game-noor-oasis.apk" "$DIST/game-noor-oasis.apk"
fi

# Worker metadata only (never served): project + built directory.
/usr/bin/time -p mkdir -p "$OPENCODE_WEB_DIR"
/usr/bin/time -p python3 -c \
  'import json,sys; json.dump({"project": sys.argv[1], "directory": sys.argv[2]}, open(sys.argv[3], "w"))' \
  "$PROJECT_DIR" "$DIST" "$OPENCODE_WEB_DIR/deployment-output.json"
/usr/bin/time -p cat "$OPENCODE_WEB_DIR/deployment-output.json"
echo

# Foreground server (the controller reuses this healthy process).
exec /usr/bin/time -p python3 -m http.server "$PORT" --bind 0.0.0.0 --directory "$DIST"
