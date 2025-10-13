#!/usr/bin/env bash
set -euo pipefail

# Run unit tests for only the Maven modules changed since upstream.
# Intended to run as a pre-push hook (via pre-commit framework).

# Get repository root
ROOT_DIR=$(git rev-parse --show-toplevel)
cd "$ROOT_DIR"

# Source shared utilities
# shellcheck source=scripts/git/lib/maven_utils.sh
source "$ROOT_DIR/scripts/git/lib/maven_utils.sh"

# Create temp files for processing
FILES_TMP=$(mktemp)
MODULES_TMP=$(mktemp)

# Ensure cleanup on exit, error, or interrupt
trap 'rm -f "$FILES_TMP" "$MODULES_TMP"' EXIT ERR INT TERM

# Determine upstream branch to diff against
UPSTREAM=""
if UPSTREAM_REF=$(git rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>/dev/null); then
  UPSTREAM="$UPSTREAM_REF"
fi

# Calculate diff range
if [[ -n "$UPSTREAM" ]]; then
  DIFF_RANGE="$UPSTREAM...HEAD"
  echo "[pre-push] Comparing against upstream: $UPSTREAM"
else
  # Fallback: compare last commit
  DIFF_RANGE="HEAD~1..HEAD"
  echo "[pre-push] No upstream found, comparing against last commit (HEAD~1)"
fi

# Collect changed files (properly quoted)
git diff --name-only "$DIFF_RANGE" | grep -v '^$' | sort -u >"$FILES_TMP" || true

if [[ ! -s "$FILES_TMP" ]]; then
  echo "[pre-push] No changes detected relative to upstream; skipping tests."
  exit 0
fi

# Collect affected modules
collect_affected_modules "$ROOT_DIR" < "$FILES_TMP" > "$MODULES_TMP"

if [[ ! -s "$MODULES_TMP" ]]; then
  echo "[pre-push] No Maven modules affected; skipping tests."
  exit 0
fi

# Display affected modules
echo "[pre-push] Affected modules:"
sed 's/^/  - /' "$MODULES_TMP"

# Run fmt:check to verify formatting compliance
echo ""
echo "[pre-push] Step 1/2: Checking Java formatting compliance..."
if grep -qxF '.' "$MODULES_TMP"; then
  # Root module affected - check entire project
  run_maven_on_modules "$ROOT_DIR" "$MODULES_TMP" \
    com.spotify.fmt:fmt-maven-plugin:check
else
  # Check only affected modules
  run_maven_on_modules "$ROOT_DIR" "$MODULES_TMP" \
    com.spotify.fmt:fmt-maven-plugin:check
fi

# Run tests
echo ""
echo "[pre-push] Step 2/2: Running unit tests..."
if grep -qxF '.' "$MODULES_TMP"; then
  # Root module affected - test entire project
  run_maven_on_modules "$ROOT_DIR" "$MODULES_TMP" \
    -DfailIfNoTests=false test
else
  # Test only affected modules
  run_maven_on_modules "$ROOT_DIR" "$MODULES_TMP" \
    -DfailIfNoTests=false test
fi

echo ""
echo "✅ Pre-push checks passed!"
