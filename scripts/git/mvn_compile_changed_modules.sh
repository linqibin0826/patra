#!/usr/bin/env bash
set -euo pipefail

# Compile only the Maven modules affected by the staged changes.
# Usage: invoked by pre-commit with the list of changed files, but can also
# be run manually without args (falls back to staged changes).
#
# SKIP FLAGS:
#   SKIP_COMPILE=1  - Skip compilation check
#   SKIP_FORMAT=1   - Skip formatting step
#   SKIP_HOOKS=1    - Skip all pre-commit hooks

# Get repository root
ROOT_DIR=$(git rev-parse --show-toplevel)
cd "$ROOT_DIR"

# Source shared utilities
# shellcheck source=scripts/git/lib/maven_utils.sh
source "$ROOT_DIR/scripts/git/lib/maven_utils.sh"

# Check for skip flags
if should_skip "COMPILE"; then
  exit 0
fi

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

# Collect affected modules (use parallel processing for speed)
echo "[pre-commit] Detecting affected modules..."
if [[ -s "$FILES_TMP" ]]; then
  # Use parallel processing if more than 10 files
  file_count=$(wc -l < "$FILES_TMP")
  if [[ "$file_count" -gt 10 ]]; then
    collect_affected_modules_parallel "$ROOT_DIR" < "$FILES_TMP" > "$MODULES_TMP"
  else
    collect_affected_modules "$ROOT_DIR" < "$FILES_TMP" > "$MODULES_TMP"
  fi
else
  touch "$MODULES_TMP"
fi

# Check if any modules were affected
if [[ ! -s "$MODULES_TMP" ]]; then
  echo "[pre-commit] No Maven modules affected; skipping compile."
  exit 0
fi

# Display affected modules
module_count=$(wc -l < "$MODULES_TMP")
echo "[pre-commit] Affected modules ($module_count):"
sed 's/^/  - /' "$MODULES_TMP"

# Step 1: Auto-format Java sources
echo ""
echo "[pre-commit] Step 1/2: Auto-formatting Java sources..."
if ! should_skip "FORMAT"; then
  if grep -qxF '.' "$MODULES_TMP"; then
    # Root module affected - format entire project
    run_maven_with_cache "format" "$ROOT_DIR" "$MODULES_TMP" "$FILES_TMP" \
      com.spotify.fmt:fmt-maven-plugin:format
  else
    # Format only affected modules
    run_maven_with_cache "format" "$ROOT_DIR" "$MODULES_TMP" "$FILES_TMP" \
      com.spotify.fmt:fmt-maven-plugin:format
  fi
else
  echo "✓ Skipped (SKIP_FORMAT=1)"
fi

# Step 2: Compile
echo ""
echo "[pre-commit] Step 2/2: Compiling..."
if grep -qxF '.' "$MODULES_TMP"; then
  # Root module affected - compile entire project
  run_maven_with_cache "compile" "$ROOT_DIR" "$MODULES_TMP" "$FILES_TMP" \
    -DskipTests compile
else
  # Compile only affected modules
  run_maven_with_cache "compile" "$ROOT_DIR" "$MODULES_TMP" "$FILES_TMP" \
    -DskipTests compile
fi

echo ""
echo "✅ Pre-commit checks passed!"
echo ""
echo "💡 TIP: Use skip flags to speed up iteration:"
echo "   SKIP_COMPILE=1 git commit -m '...'  # Skip compilation"
echo "   SKIP_FORMAT=1 git commit -m '...'   # Skip formatting"
echo "   SKIP_HOOKS=1 git commit -m '...'    # Skip all hooks"
