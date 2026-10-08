#!/usr/bin/env bash
# Capture desktop + mobile screenshots of $CAPTURE_URL into $CAPTURE_DIR.
# Exit 75 = temporary navigation/browser infrastructure failure (retryable).
# Exit 1  = script usage error or rendering defect (page blank / content missing).
set -euo pipefail

T="/usr/bin/time -p"
RC=1
SESS_D="noor-cap-desktop-$$"
SESS_M="noor-cap-mobile-$$"
CLEANED=0
cleanup() {
  if [[ "$CLEANED" == 0 ]]; then
    CLEANED=1
    $T playwright-cli -s="$SESS_D" close >/dev/null 2>&1 || true
    $T playwright-cli -s="$SESS_M" close >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

die() { echo "capture: $1" >&2; RC="$2"; exit "$RC"; }

# --- script inputs (usage errors are defects, not transient) ---
[[ -n "${CAPTURE_URL:-}" ]] || die "CAPTURE_URL env var is required." 1
[[ -n "${CAPTURE_DIR:-}" ]] || die "CAPTURE_DIR env var is required." 1
$T mkdir -p "$CAPTURE_DIR"
$T test -d "$CAPTURE_DIR" || die "Cannot create CAPTURE_DIR=$CAPTURE_DIR." 1

# --- origin reachability: connection failures / 5xx are temporary (exit 75) ---
$T curl --silent --show-error --location --max-time 20 --output /dev/null --write-out 'http:%{http_code}\n' "$CAPTURE_URL" > /tmp/noor-cap-probe-$$.txt 2>/dev/null \
  || die "Probe of $CAPTURE_URL failed (network/tunnel)." 75
CODE="$(tail -1 /tmp/noor-cap-probe-$$.txt | tr -dc '0-9')"
rm -f /tmp/noor-cap-probe-$$.txt
[[ "$CODE" == 2* || "$CODE" == 3* ]] || {
  [[ "$CODE" == 5* ]] && die "Origin returned HTTP $CODE (temporary)." 75
  die "Origin returned HTTP $CODE (defect)." 1
}

# Wait until the page is actually rendered (readyState + visible text).
wait_rendered() { # $1 = session
  local sess="$1" i=0 raw rc out state rest len evalFails=0
  for ((i = 0; i < 30; i++)); do
    raw="$($T playwright-cli -s="$sess" eval "() => document.readyState + '|' + document.title + '|' + document.body.innerText.length" 2>/dev/null)"
    rc=$?
    if [[ $rc == 0 ]]; then
      out="$(printf '%s\n' "$raw" | grep -a -o 'complete|[^"]*|[0-9]*' | tail -1)"
      echo "render-state[$sess]: $out"
      rest="${out#*|}"
      len="${rest##*|}"
      if [[ -n "$out" && "$len" =~ ^[0-9]+$ && "$len" -gt 50 ]]; then
        return 0
      fi
    else
      evalFails=$((evalFails + 1))
      echo "render-state[$sess]: eval failed ($evalFails), retrying..."
    fi
    $T sleep 2
  done
  [[ $evalFails -ge 28 ]] && return 75
  return 1
}

shot_ok() { # $1 = file : exists and non-trivial size (blank pages are tiny)
  local f="$1" sz
  $T test -f "$f" || return 1
  sz="$($T stat -c %s "$f")"
  echo "screenshot: $f (${sz} bytes)"
  [[ "$sz" -gt 10240 ]]
}

# --- desktop view (own browser session; system Chrome, no downloads) ---
if ! $T timeout 100 playwright-cli -s="$SESS_D" open --browser chrome "$CAPTURE_URL" >/tmp/noor-cap-desk-$$.log 2>&1; then
  tail -5 /tmp/noor-cap-desk-$$.log >&2 || true
  die "Desktop navigation failed (browser/infra)." 75
fi
rc=0; wait_rendered "$SESS_D" || rc=$?
[[ $rc == 0 ]] || { [[ $rc == 75 ]] && die "Desktop session unresponsive (browser/infra)." 75; die "Desktop page did not render content." 1; }
$T timeout 60 playwright-cli -s="$SESS_D" screenshot --filename "$CAPTURE_DIR/final-desktop.png" \
  || die "Desktop screenshot failed (browser/infra)." 75
shot_ok "$CAPTURE_DIR/final-desktop.png" || die "Desktop screenshot is blank/tiny (rendering defect)." 1

# --- mobile view (own browser session, device emulation; resize fallback) ---
if $T timeout 100 playwright-cli -s="$SESS_M" open --browser chrome --device "iphone 15" "$CAPTURE_URL" >/tmp/noor-cap-mob-$$.log 2>&1; then
  :
else
  echo "device emulation unavailable, falling back to 390x844 viewport." >&2
  $T timeout 100 playwright-cli -s="$SESS_M" open --browser chrome "$CAPTURE_URL" >/tmp/noor-cap-mob-$$.log 2>&1 \
    || { tail -5 /tmp/noor-cap-mob-$$.log >&2 || true; die "Mobile navigation failed (browser/infra)." 75; }
  $T timeout 30 playwright-cli -s="$SESS_M" resize 390 844 >/dev/null 2>&1 \
    || die "Mobile resize failed (browser/infra)." 75
  $T timeout 60 playwright-cli -s="$SESS_M" reload >/dev/null 2>&1 || true
fi
rc=0; wait_rendered "$SESS_M" || rc=$?
[[ $rc == 0 ]] || { [[ $rc == 75 ]] && die "Mobile session unresponsive (browser/infra)." 75; die "Mobile page did not render content." 1; }
$T timeout 60 playwright-cli -s="$SESS_M" screenshot --filename "$CAPTURE_DIR/final-mobile.png" \
  || die "Mobile screenshot failed (browser/infra)." 75
shot_ok "$CAPTURE_DIR/final-mobile.png" || die "Mobile screenshot is blank/tiny (rendering defect)." 1

# --- close own browsers; app server keeps running ---
$T playwright-cli -s="$SESS_D" close >/dev/null 2>&1 || true
$T playwright-cli -s="$SESS_M" close >/dev/null 2>&1 || true
echo "capture: OK -> $CAPTURE_DIR/final-desktop.png + $CAPTURE_DIR/final-mobile.png"
RC=0
exit 0
