#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
PROJECT_DIR="$(pwd)"
PORT="${PORT:-3000}"
DIST_DIR="$PROJECT_DIR/dist"
WEB_DIR="${OPENCODE_WEB_DIR:-/home/runner/work/_temp/omgithub-web}"
/usr/bin/time -p mkdir -p "$DIST_DIR"
/usr/bin/time -p mkdir -p "$WEB_DIR"
if /usr/bin/time -p test -f "$PROJECT_DIR/package.json"; then
  if /usr/bin/time -p test -f "$PROJECT_DIR/package-lock.json"; then
    /usr/bin/time -p npm ci --no-audit --no-fund
  else
    /usr/bin/time -p npm install --no-audit --no-fund
  fi
  if PROJECT_DIR="$PROJECT_DIR" /usr/bin/time -p node -e "const p=require(process.env.PROJECT_DIR+'/package.json');process.exit(p.scripts&&p.scripts.build?0:1)"; then
    /usr/bin/time -p npm run build
  fi
fi
/usr/bin/time -p test -f "$DIST_DIR/index.html"
PROJECT_DIR="$PROJECT_DIR" DIST_DIR="$DIST_DIR" WEB_DIR="$WEB_DIR" /usr/bin/time -p node -e '
const fs = require("fs");
const path = require("path");
const project = process.env.PROJECT_DIR || process.cwd();
const dir = process.env.DIST_DIR || path.join(project, "dist");
const web = process.env.WEB_DIR || "/home/runner/work/_temp/omgithub-web";
fs.mkdirSync(web, { recursive: true });
fs.writeFileSync(path.join(web, "deployment-output.json"), JSON.stringify({ project: project, directory: dir }));
console.log("deployment-output: " + path.join(web, "deployment-output.json") + " -> " + dir);
'
exec /usr/bin/time -p python3 -m http.server "$PORT" --directory "$DIST_DIR" --bind 0.0.0.0
