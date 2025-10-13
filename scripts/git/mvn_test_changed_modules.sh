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

readarray -t FILES < <(git diff --name-only $DIFF_RANGE | sed '/^$/d' | sort -u)

if [[ ${#FILES[@]} -eq 0 ]]; then
  echo "[pre-push] No changes detected relative to upstream; skipping tests."
  exit 0
fi

# Reuse module detection logic inline to keep this script standalone.
declare -A MODULES=()
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
  echo "[pre-push] No Maven modules affected; skipping tests."
  exit 0
fi

if [[ -n "${MODULES[.]:-}" ]]; then
  echo "[pre-push] Running: ./mvnw -q -T1C -DfailIfNoTests=false test"
  ./mvnw -q -T1C -DfailIfNoTests=false test
  exit 0
fi

MODULE_LIST=$(printf '%s\n' "${!MODULES[@]}" | sort -u | paste -sd, -)
echo "[pre-push] Running: ./mvnw -q -T1C -DfailIfNoTests=false -pl ${MODULE_LIST} -am test"
./mvnw -q -T1C -DfailIfNoTests=false -pl "${MODULE_LIST}" -am test

