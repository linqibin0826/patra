#!/usr/bin/env bash
set -euo pipefail

# Compile only the Maven modules affected by the staged changes.
# Usage: invoked by pre-commit with the list of changed files, but can also
# be run manually without args (falls back to staged changes).

ROOT_DIR=$(git rev-parse --show-toplevel)
cd "$ROOT_DIR"

FILES_TMP=$(mktemp)
if [ "$#" -gt 0 ]; then
  for f in "$@"; do
    printf '%s\n' "$f" >>"$FILES_TMP"
  done
else
  git diff --cached --name-only --diff-filter=ACMR >>"$FILES_TMP"
fi
sed -i '' -e '/^$/d' "$FILES_TMP" 2>/dev/null || sed -e '/^$/d' -i "$FILES_TMP" 2>/dev/null || true
sort -u "$FILES_TMP" -o "$FILES_TMP"

MODULES_TMP=$(mktemp)
>"$MODULES_TMP"

find_module_for_file() {
  local f="$1"
  # Skip deleted files or paths outside repo
  [[ -e "$f" || -L "$f" ]] || return 0

  local dir
  dir=$(dirname -- "$f")
  # Walk up until we find a pom.xml or reach repo root
  while true; do
    if [[ -f "$dir/pom.xml" ]]; then
      # If the module is the repo root, mark as '.' (compile all)
      if [[ "$dir" == "$ROOT_DIR" ]]; then
        printf '.\n'
      else
        # Print relative path to root
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
  echo "[pre-commit] No Maven modules affected; skipping compile."
  exit 0
fi

# If root aggregator is affected, compile everything once.
if grep -qxF '.' "$MODULES_TMP"; then
  echo "[pre-commit] Running: ./mvnw -q -T1C com.spotify.fmt:fmt-maven-plugin:format"
  ./mvnw -q -T1C com.spotify.fmt:fmt-maven-plugin:format
  echo "[pre-commit] Running: ./mvnw -q -DskipTests -T1C compile"
  ./mvnw -q -DskipTests -T1C compile
  exit 0
fi

MODULE_LIST=$(paste -sd, "$MODULES_TMP")

# First auto-format Java sources to align with patra-parent fmt plugin
echo "[pre-commit] Running: ./mvnw -q -T1C -pl ${MODULE_LIST} -am com.spotify.fmt:fmt-maven-plugin:format"
./mvnw -q -T1C -pl "${MODULE_LIST}" -am com.spotify.fmt:fmt-maven-plugin:format

# Then compile (validate phase will re-run fmt:check if bound there)
echo "[pre-commit] Running: ./mvnw -q -DskipTests -T1C -pl ${MODULE_LIST} -am compile"
./mvnw -q -DskipTests -T1C -pl "${MODULE_LIST}" -am compile

rm -f "$FILES_TMP" "$MODULES_TMP" 2>/dev/null || true
