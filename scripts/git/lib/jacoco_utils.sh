#!/usr/bin/env bash
# JaCoCo utilities for layer-specific coverage thresholds
# Usage: source scripts/git/lib/jacoco_utils.sh

# Layer-specific coverage thresholds (percentage)
# Based on hexagonal architecture layers
declare -A COVERAGE_THRESHOLDS=(
  ["domain"]=85    # Core business logic - highest coverage required
  ["app"]=75       # Orchestration layer - high coverage
  ["infra"]=70     # Infrastructure - moderate coverage
  ["adapter"]=60   # Adapters - lower coverage (integration-heavy)
  ["default"]=70   # Default for other modules
)

# Get coverage threshold for a module
# Args:
#   $1 - Module path (e.g., "patra-ingest/patra-ingest-domain")
# Returns: Coverage threshold percentage
get_coverage_threshold() {
  local module_path="$1"
  local module_name
  module_name=$(basename "$module_path")

  # Determine layer from module name
  if [[ "$module_name" == *"-domain" ]]; then
    echo "${COVERAGE_THRESHOLDS[domain]}"
  elif [[ "$module_name" == *"-app" ]]; then
    echo "${COVERAGE_THRESHOLDS[app]}"
  elif [[ "$module_name" == *"-infra" ]]; then
    echo "${COVERAGE_THRESHOLDS[infra]}"
  elif [[ "$module_name" == *"-adapter" ]]; then
    echo "${COVERAGE_THRESHOLDS[adapter]}"
  else
    echo "${COVERAGE_THRESHOLDS[default]}"
  fi
}

# Check JaCoCo coverage for a module
# Args:
#   $1 - Root directory
#   $2 - Module path
# Returns: 0 if coverage meets threshold, 1 otherwise
check_module_coverage() {
  local root_dir="$1"
  local module_path="$2"
  local threshold
  threshold=$(get_coverage_threshold "$module_path")

  local report_file="$root_dir/$module_path/target/site/jacoco/index.html"

  if [[ ! -f "$report_file" ]]; then
    echo "[jacoco] WARNING: No coverage report found for $module_path" >&2
    return 0  # Don't fail if report missing (module may have no tests)
  fi

  # Extract coverage percentage from HTML report
  # Look for line like: <td class="ctr2">85%</td>
  local coverage
  coverage=$(grep -oP 'Total.*?<td class="ctr2">\K\d+' "$report_file" | head -1 || echo "0")

  echo "[jacoco] $module_path: ${coverage}% coverage (threshold: ${threshold}%)" >&2

  if [[ "$coverage" -lt "$threshold" ]]; then
    echo "[jacoco] ❌ FAILED: Coverage ${coverage}% is below threshold ${threshold}%" >&2
    return 1
  else
    echo "[jacoco] ✅ PASSED: Coverage ${coverage}% meets threshold ${threshold}%" >&2
    return 0
  fi
}

# Check coverage for all modules in a file
# Args:
#   $1 - Root directory
#   $2 - Modules file path
# Returns: 0 if all modules meet thresholds, 1 otherwise
check_all_modules_coverage() {
  local root_dir="$1"
  local modules_file="$2"
  local failed=0

  echo "[jacoco] Checking coverage thresholds for affected modules..."
  echo ""

  while IFS= read -r module; do
    if [[ "$module" == "." ]]; then
      # Skip root aggregator
      continue
    fi

    if ! check_module_coverage "$root_dir" "$module"; then
      failed=1
    fi
  done < "$modules_file"

  echo ""
  if [[ "$failed" -eq 1 ]]; then
    echo "[jacoco] ❌ Some modules failed coverage thresholds"
    return 1
  else
    echo "[jacoco] ✅ All modules meet coverage thresholds"
    return 0
  fi
}
