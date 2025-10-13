#!/usr/bin/env bash
set -euo pipefail

# Run unit tests for only the Maven modules changed since upstream.
# Intended to run as a pre-commit hook with stage=push (via pre-commit framework).

ROOT_DIR=$(git rev-parse --show-toplevel)
cd "$ROOT_DIR"

# Determine upstream branch to diff against. Fallback to last commit if none.
UPSTREAM=""
if UPSTREAM_REF=$(git rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>/dev/null); then
  UPSTREAM="$UPSTREAM_REF"
fi

if [[ -n "$UPSTREAM" ]]; then
  DIFF_RANGE="$UPSTREAM...HEAD"
else
  # Fallback: compare last commit
  DIFF_RANGE="HEAD~1..HEAD"
fi

FILES_TMP=$(mktemp)
git diff --name-only $DIFF_RANGE | sed '/^$/d' | sort -u >"$FILES_TMP"

if [ ! -s "$FILES_TMP" ]; then
  echo "[pre-push] No changes detected relative to upstream; skipping tests."
  exit 0
fi

# Reuse module detection logic inline to keep this script standalone.
MODULES_TMP=$(mktemp)
>"$MODULES_TMP"
find_module_for_file() {
  local f="$1"
  [[ -e "$f" || -L "$f" ]] || return 0
  local dir
  dir=$(dirname -- "$f")
  while true; do
    if [[ -f "$dir/pom.xml" ]]; then
      if [[ "$dir" == "$ROOT_DIR" ]]; then
        printf '.'
      else
        python3 - "$dir" "$ROOT_DIR" <<'PY'
import os, sys
print(os.path.relpath(sys.argv[1], sys.argv[2]))
PY
      fi
      return 0
    fi
    [[ "$dir" == "/" || "$dir" == "." || "$dir" == "$ROOT_DIR" ]] && break
    dir=$(dirname -- "$dir")
  done
}

while IFS= read -r f; do
  mod=$(find_module_for_file "$f" || true)
  [ -z "$mod" ] && continue
  printf '%s\n' "$mod" >>"$MODULES_TMP"
done <"$FILES_TMP"
sort -u "$MODULES_TMP" -o "$MODULES_TMP"

if [ ! -s "$MODULES_TMP" ]; then
  echo "[pre-push] No Maven modules affected; skipping tests."
  exit 0
fi

if grep -qxF '.' "$MODULES_TMP"; then
  echo "[pre-push] Running: ./mvnw -q -T1C com.spotify.fmt:fmt-maven-plugin:check"
  ./mvnw -q -T1C com.spotify.fmt:fmt-maven-plugin:check
  echo "[pre-push] Running: ./mvnw -q -T1C -DfailIfNoTests=false test"
  ./mvnw -q -T1C -DfailIfNoTests=false test
  exit 0
fi

MODULE_LIST=$(paste -sd, "$MODULES_TMP")
echo "[pre-push] Running: ./mvnw -q -T1C -pl ${MODULE_LIST} -am com.spotify.fmt:fmt-maven-plugin:check"
./mvnw -q -T1C -pl "${MODULE_LIST}" -am com.spotify.fmt:fmt-maven-plugin:check
echo "[pre-push] Running: ./mvnw -q -T1C -DfailIfNoTests=false -pl ${MODULE_LIST} -am test"
./mvnw -q -T1C -DfailIfNoTests=false -pl "${MODULE_LIST}" -am test

rm -f "$FILES_TMP" "$MODULES_TMP" 2>/dev/null || true
