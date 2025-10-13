#!/usr/bin/env bash
set -euo pipefail

# Compile only the Maven modules affected by the staged changes.
# Usage: invoked by pre-commit with the list of changed files, but can also
# be run manually without args (falls back to staged changes).

ROOT_DIR=$(git rev-parse --show-toplevel)
cd "$ROOT_DIR"

readarray -t FILES < <(
  if [[ $# -gt 0 ]]; then
    printf '%s\n' "$@"
  else
    git diff --cached --name-only --diff-filter=ACMR
  fi | sed '/^$/d' | sort -u
)

declare -A MODULES=()

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
        printf '.'
      else
        # Print relative path to root
        python3 - <<'PY'
import os,sys
print(os.path.relpath(sys.argv[1], sys.argv[2]))
PY
 "$dir" "$ROOT_DIR"
      fi
      return 0
    fi
    [[ "$dir" == "/" || "$dir" == "." || "$dir" == "$ROOT_DIR" ]] && break
    dir=$(dirname -- "$dir")
  done
}

for f in "${FILES[@]}"; do
  mod=$(find_module_for_file "$f" || true)
  [[ -z "${mod:-}" ]] && continue
  MODULES["$mod"]=1
done

if [[ ${#MODULES[@]} -eq 0 ]]; then
  echo "[pre-commit] No Maven modules affected; skipping compile."
  exit 0
fi

# If root aggregator is affected, compile everything once.
if [[ -n "${MODULES[.]:-}" ]]; then
  echo "[pre-commit] Running: ./mvnw -q -DskipTests -T1C compile"
  ./mvnw -q -DskipTests -T1C compile
  exit 0
fi

# Build a comma-separated -pl list
MODULE_LIST=$(printf '%s\n' "${!MODULES[@]}" | sort -u | paste -sd, -)
echo "[pre-commit] Running: ./mvnw -q -DskipTests -T1C -pl ${MODULE_LIST} -am compile"
./mvnw -q -DskipTests -T1C -pl "${MODULE_LIST}" -am compile

