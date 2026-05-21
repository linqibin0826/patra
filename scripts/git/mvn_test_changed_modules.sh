#!/usr/bin/env bash
set -euo pipefail

# Run unit tests for only the Maven modules changed since upstream.
# Intended to run as a pre-push hook (via pre-commit framework).
#
# SKIP FLAGS:
#   SKIP_TESTS=1    - Skip test execution
#   SKIP_FORMAT=1   - Skip formatting check
#   SKIP_HOOKS=1    - Skip all pre-push hooks

# Get repository root
ROOT_DIR=$(git rev-parse --show-toplevel)
cd "$ROOT_DIR"

# Source shared utilities
# shellcheck source=scripts/git/lib/maven_utils.sh
source "$ROOT_DIR/scripts/git/lib/maven_utils.sh"
# shellcheck source=scripts/git/lib/jacoco_utils.sh
source "$ROOT_DIR/scripts/git/lib/jacoco_utils.sh"

# Check for skip flags
if should_skip "TESTS"; then
  exit 0
fi

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

# Collect affected modules (use parallel processing for speed)
echo "[pre-push] Detecting affected modules..."
file_count=$(wc -l < "$FILES_TMP")
if [[ "$file_count" -gt 10 ]]; then
  collect_affected_modules_parallel "$ROOT_DIR" < "$FILES_TMP" > "$MODULES_TMP"
else
  collect_affected_modules "$ROOT_DIR" < "$FILES_TMP" > "$MODULES_TMP"
fi

if [[ ! -s "$MODULES_TMP" ]]; then
  echo "[pre-push] No Maven modules affected; skipping tests."
  exit 0
fi

# Display affected modules
module_count=$(wc -l < "$MODULES_TMP")
echo "[pre-push] Affected modules ($module_count):"
sed 's/^/  - /' "$MODULES_TMP"

# Step 1: Check formatting compliance
echo ""
echo "[pre-push] Step 1/2: Checking Java formatting compliance..."
if ! should_skip "FORMAT"; then
  if grep -qxF '.' "$MODULES_TMP"; then
    # Root module affected - check entire project
    run_maven_with_cache "fmt-check" "$ROOT_DIR" "$MODULES_TMP" "$FILES_TMP" \
      com.spotify.fmt:fmt-maven-plugin:check
  else
    # Check only affected modules
    run_maven_with_cache "fmt-check" "$ROOT_DIR" "$MODULES_TMP" "$FILES_TMP" \
      com.spotify.fmt:fmt-maven-plugin:check
  fi
else
  echo "✓ Skipped (SKIP_FORMAT=1)"
fi

# Step 2: Run tests
echo ""
echo "[pre-push] Step 2/2: Running unit tests..."
if grep -qxF '.' "$MODULES_TMP"; then
  # Root module affected - test entire project
  run_maven_with_cache "test" "$ROOT_DIR" "$MODULES_TMP" "$FILES_TMP" \
    -DfailIfNoTests=false test
else
  # Test only affected modules
  run_maven_with_cache "test" "$ROOT_DIR" "$MODULES_TMP" "$FILES_TMP" \
    -DfailIfNoTests=false test
fi

# Optional Step 3: Check coverage thresholds (disabled by default)
if [[ "${CHECK_COVERAGE:-0}" == "1" ]] && ! should_skip "JACOCO"; then
  echo ""
  echo "[pre-push] Step 3/3: Checking test coverage thresholds..."
  if check_all_modules_coverage "$ROOT_DIR" "$MODULES_TMP"; then
    echo "✅ Coverage check passed!"
  else
    echo ""
    echo "❌ Coverage check failed!"
    echo ""
    echo "💡 Coverage thresholds by layer:"
    echo "   • domain:  85% (core business logic)"
    echo "   • app:     75% (orchestration)"
    echo "   • infra:   70% (infrastructure)"
    echo "   • adapter: 60% (adapters)"
    echo ""
    echo "📊 View detailed reports:"
    echo "   find . -name 'index.html' -path '*/jacoco/index.html'"
    exit 1
  fi
fi

echo ""
echo "✅ Pre-push checks passed!"
echo ""
echo "💡 TIP: Use skip flags to speed up iteration:"
echo "   SKIP_TESTS=1 git push        # Skip tests"
echo "   SKIP_FORMAT=1 git push       # Skip formatting check"
echo "   SKIP_JACOCO=1 git push       # Skip coverage check"
echo "   SKIP_HOOKS=1 git push        # Skip all hooks"
echo "   git push --no-verify         # Skip all hooks (alternative)"
echo ""
echo "📊 Enable coverage checks:"
echo "   CHECK_COVERAGE=1 git push    # Enforce coverage thresholds"
