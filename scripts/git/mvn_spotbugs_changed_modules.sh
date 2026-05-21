#!/usr/bin/env bash
set -euo pipefail

# Run SpotBugs static analysis for only the Maven modules changed.
# Detects common Java bugs, security vulnerabilities, and code quality issues.
#
# SKIP FLAGS:
#   SKIP_SPOTBUGS=1 - Skip SpotBugs analysis
#   SKIP_HOOKS=1    - Skip all pre-push hooks

# Get repository root
ROOT_DIR=$(git rev-parse --show-toplevel)
cd "$ROOT_DIR"

# Source shared utilities
# shellcheck source=scripts/git/lib/maven_utils.sh
source "$ROOT_DIR/scripts/git/lib/maven_utils.sh"

# Check for skip flags
if should_skip "SPOTBUGS"; then
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
  echo "[spotbugs] Comparing against upstream: $UPSTREAM"
else
  # Fallback: compare last commit
  DIFF_RANGE="HEAD~1..HEAD"
  echo "[spotbugs] No upstream found, comparing against last commit (HEAD~1)"
fi

# Collect changed files (properly quoted)
git diff --name-only "$DIFF_RANGE" | grep -v '^$' | sort -u >"$FILES_TMP" || true

if [[ ! -s "$FILES_TMP" ]]; then
  echo "[spotbugs] No changes detected relative to upstream; skipping SpotBugs analysis."
  exit 0
fi

# Only check Java files
if [[ -s "$FILES_TMP" ]]; then
  grep '\.java$' "$FILES_TMP" > "${FILES_TMP}.tmp" || touch "${FILES_TMP}.tmp"
  mv "${FILES_TMP}.tmp" "$FILES_TMP"
fi

# Check if any Java files were affected
if [[ ! -s "$FILES_TMP" ]]; then
  echo "[spotbugs] No Java files changed; skipping SpotBugs analysis."
  exit 0
fi

# Collect affected modules (use parallel processing for speed)
echo "[spotbugs] Detecting affected modules..."
file_count=$(wc -l < "$FILES_TMP")
if [[ "$file_count" -gt 10 ]]; then
  collect_affected_modules_parallel "$ROOT_DIR" < "$FILES_TMP" > "$MODULES_TMP"
else
  collect_affected_modules "$ROOT_DIR" < "$FILES_TMP" > "$MODULES_TMP"
fi

if [[ ! -s "$MODULES_TMP" ]]; then
  echo "[spotbugs] No Maven modules affected; skipping SpotBugs analysis."
  exit 0
fi

# Display affected modules
module_count=$(wc -l < "$MODULES_TMP")
echo "[spotbugs] Affected modules ($module_count):"
sed 's/^/  - /' "$MODULES_TMP"

# Run SpotBugs analysis
echo ""
echo "[spotbugs] Running SpotBugs static analysis..."
echo "[spotbugs] This may take a few moments..."
if grep -qxF '.' "$MODULES_TMP"; then
  # Root module affected - analyze entire project
  run_maven_with_cache "spotbugs" "$ROOT_DIR" "$MODULES_TMP" "$FILES_TMP" \
    spotbugs:check
else
  # Analyze only affected modules
  run_maven_with_cache "spotbugs" "$ROOT_DIR" "$MODULES_TMP" "$FILES_TMP" \
    spotbugs:check
fi

echo ""
echo "✅ SpotBugs analysis passed!"
echo ""
echo "🔍 Bug categories checked:"
echo "   • Null pointer dereferences"
echo "   • Resource leaks (unclosed streams, connections)"
echo "   • Concurrency issues (thread safety)"
echo "   • Security vulnerabilities (SQL injection, XSS)"
echo "   • Incorrect implementations (equals/hashCode)"
echo ""
echo "📊 Reports generated at: target/spotbugs/*.html"
echo ""
echo "💡 TIP: Skip with SKIP_SPOTBUGS=1 git push"
