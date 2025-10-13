#!/usr/bin/env bash
set -euo pipefail

# Compile only the Maven modules affected by the staged changes.
# Usage: invoked by pre-commit with the list of changed files, but can also
# be run manually without args (falls back to staged changes).

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

# Collect changed files
if [ "$#" -gt 0 ]; then
  # Files passed as arguments (from pre-commit)
  printf '%s\n' "$@" >"$FILES_TMP"
else
  # Fallback: get staged changes
  git diff --cached --name-only --diff-filter=ACMR >"$FILES_TMP"
fi

# Remove empty lines using portable grep
if [[ -s "$FILES_TMP" ]]; then
  grep -v '^$' "$FILES_TMP" > "${FILES_TMP}.tmp" && mv "${FILES_TMP}.tmp" "$FILES_TMP" || true
fi

# Sort and deduplicate
sort -u "$FILES_TMP" -o "$FILES_TMP"

# Collect affected modules
if [[ -s "$FILES_TMP" ]]; then
  collect_affected_modules "$ROOT_DIR" < "$FILES_TMP" > "$MODULES_TMP"
else
  touch "$MODULES_TMP"
fi

# Check if any modules were affected
if [[ ! -s "$MODULES_TMP" ]]; then
  echo "[pre-commit] No Maven modules affected; skipping compile."
  exit 0
fi

# Display affected modules
echo "[pre-commit] Affected modules:"
sed 's/^/  - /' "$MODULES_TMP"

# Run fmt:format to auto-format Java sources
echo ""
echo "[pre-commit] Step 1/2: Auto-formatting Java sources..."
if grep -qxF '.' "$MODULES_TMP"; then
  # Root module affected - format entire project
  run_maven_on_modules "$ROOT_DIR" "$MODULES_TMP" \
    com.spotify.fmt:fmt-maven-plugin:format
else
  # Format only affected modules
  run_maven_on_modules "$ROOT_DIR" "$MODULES_TMP" \
    com.spotify.fmt:fmt-maven-plugin:format
fi

# Run compile phase
echo ""
echo "[pre-commit] Step 2/2: Compiling..."
if grep -qxF '.' "$MODULES_TMP"; then
  # Root module affected - compile entire project
  run_maven_on_modules "$ROOT_DIR" "$MODULES_TMP" \
    -DskipTests compile
else
  # Compile only affected modules
  run_maven_on_modules "$ROOT_DIR" "$MODULES_TMP" \
    -DskipTests compile
fi

echo ""
echo "✅ Pre-commit checks passed!"
