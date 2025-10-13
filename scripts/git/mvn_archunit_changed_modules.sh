#!/usr/bin/env bash
set -euo pipefail

# Run ArchUnit architecture tests for only the Maven modules changed.
# Validates hexagonal architecture rules and DDD patterns.
#
# SKIP FLAGS:
#   SKIP_ARCHUNIT=1 - Skip ArchUnit validation
#   SKIP_HOOKS=1    - Skip all pre-commit hooks

# Get repository root
ROOT_DIR=$(git rev-parse --show-toplevel)
cd "$ROOT_DIR"

# Source shared utilities
# shellcheck source=scripts/git/lib/maven_utils.sh
source "$ROOT_DIR/scripts/git/lib/maven_utils.sh"

# Check for skip flags
if should_skip "ARCHUNIT"; then
  exit 0
fi

# Create temp files for processing
FILES_TMP=$(mktemp)
MODULES_TMP=$(mktemp)
BOOT_MODULES_TMP=$(mktemp)

# Ensure cleanup on exit, error, or interrupt
trap 'rm -f "$FILES_TMP" "$MODULES_TMP" "$BOOT_MODULES_TMP"' EXIT ERR INT TERM

# Collect changed files
if [ "$#" -gt 0 ]; then
  # Files passed as arguments
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

# Only check Java files
if [[ -s "$FILES_TMP" ]]; then
  grep '\.java$' "$FILES_TMP" > "${FILES_TMP}.tmp" || touch "${FILES_TMP}.tmp"
  mv "${FILES_TMP}.tmp" "$FILES_TMP"
fi

# Check if any Java files were affected
if [[ ! -s "$FILES_TMP" ]]; then
  echo "[archunit] No Java files changed; skipping ArchUnit validation."
  exit 0
fi

# Collect affected modules
echo "[archunit] Detecting affected modules..."
file_count=$(wc -l < "$FILES_TMP")
if [[ "$file_count" -gt 10 ]]; then
  collect_affected_modules_parallel "$ROOT_DIR" < "$FILES_TMP" > "$MODULES_TMP"
else
  collect_affected_modules "$ROOT_DIR" < "$FILES_TMP" > "$MODULES_TMP"
fi

if [[ ! -s "$MODULES_TMP" ]]; then
  echo "[archunit] No Maven modules affected; skipping ArchUnit validation."
  exit 0
fi

# Filter to only -boot modules (where ArchUnit tests reside)
# ArchUnit tests are in boot modules as they need to see all layers
>"$BOOT_MODULES_TMP"
while IFS= read -r module; do
  if [[ "$module" == *"-boot" || "$module" == "." ]]; then
    printf '%s\n' "$module" >>"$BOOT_MODULES_TMP"
  else
    # Find the parent boot module
    boot_module=$(dirname "$module" 2>/dev/null | grep -o '^[^/]*' 2>/dev/null || echo "")
    if [[ -n "$boot_module" && -d "$ROOT_DIR/$boot_module/$boot_module-boot" ]]; then
      printf '%s\n' "$boot_module/$boot_module-boot" >>"$BOOT_MODULES_TMP"
    fi
  fi
done < "$MODULES_TMP"

# Deduplicate boot modules
sort -u "$BOOT_MODULES_TMP" -o "$BOOT_MODULES_TMP"

if [[ ! -s "$BOOT_MODULES_TMP" ]]; then
  echo "[archunit] No boot modules affected; skipping ArchUnit validation."
  echo "[archunit] ArchUnit tests only run for *-boot modules."
  exit 0
fi

# Display affected boot modules
boot_count=$(wc -l < "$BOOT_MODULES_TMP")
echo "[archunit] Affected boot modules ($boot_count):"
sed 's/^/  - /' "$BOOT_MODULES_TMP"

# Run ArchUnit tests (test phase runs all tests including ArchUnit)
echo ""
echo "[archunit] Running ArchUnit architecture validation..."
if grep -qxF '.' "$BOOT_MODULES_TMP"; then
  # Root module - run all ArchUnit tests
  run_maven_with_cache "archunit" "$ROOT_DIR" "$BOOT_MODULES_TMP" "$FILES_TMP" \
    -Dsurefire.failIfNoSpecifiedTests=false -Dtest="*ArchitectureTest" test
else
  # Run ArchUnit tests only for affected boot modules
  run_maven_with_cache "archunit" "$ROOT_DIR" "$BOOT_MODULES_TMP" "$FILES_TMP" \
    -Dsurefire.failIfNoSpecifiedTests=false -Dtest="*ArchitectureTest" test
fi

echo ""
echo "✅ ArchUnit validation passed!"
echo ""
echo "📚 Architecture rules enforced:"
echo "   • Domain layer has NO Spring/framework dependencies"
echo "   • Dependency direction: Adapter → App → Domain ← Infra"
echo "   • Naming conventions (*Orchestrator, *Port, *RepositoryImpl, *DO)"
echo "   • No circular dependencies between packages"
echo ""
echo "💡 TIP: Skip with SKIP_ARCHUNIT=1 git commit -m '...'"
