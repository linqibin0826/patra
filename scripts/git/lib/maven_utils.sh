#!/usr/bin/env bash
# Shared utilities for Maven pre-commit hooks
# Usage: source scripts/git/lib/maven_utils.sh

# ============================================================================
# SKIP FLAGS
# ============================================================================

# Check if a specific check should be skipped based on environment variables.
# Args:
#   $1 - Check name (COMPILE, TESTS, FORMAT, ARCHUNIT, SPOTBUGS, JACOCO)
# Returns: 0 if should skip, 1 if should run
should_skip() {
  local check_name="$1"
  local skip_var="SKIP_${check_name}"
  local skip_all="${SKIP_HOOKS:-0}"

  # Check for SKIP_HOOKS=1 (skip everything)
  if [[ "$skip_all" == "1" ]]; then
    echo "[SKIP] All hooks disabled via SKIP_HOOKS=1" >&2
    return 0
  fi

  # Check for specific skip flag (with safe variable expansion)
  local skip_value="${!skip_var:-0}"
  if [[ "$skip_value" == "1" ]]; then
    echo "[SKIP] $check_name check disabled via $skip_var=1" >&2
    return 0
  fi

  return 1
}

# ============================================================================
# CACHING
# ============================================================================

# Cache directory for storing check results
CACHE_DIR="${GIT_DIR:-.git}/hooks/cache"

# Initialize cache directory
init_cache() {
  mkdir -p "$CACHE_DIR"
}

# Compute hash of files for caching
# Reads file paths from stdin, outputs hash
compute_files_hash() {
  local root_dir="$1"
  local hash_input=""

  while IFS= read -r f; do
    if [[ -f "$root_dir/$f" ]]; then
      # Hash file content + pom.xml mtime for dependency changes
      hash_input+=$(cat "$root_dir/$f" 2>/dev/null || echo "")
    fi
  done

  # Also include root pom.xml to detect plugin changes
  if [[ -f "$root_dir/pom.xml" ]]; then
    hash_input+=$(stat -f "%m" "$root_dir/pom.xml" 2>/dev/null || stat -c "%Y" "$root_dir/pom.xml" 2>/dev/null || echo "")
  fi

  # Generate SHA256 hash
  echo -n "$hash_input" | shasum -a 256 | cut -d' ' -f1
}

# Check if cached result exists and is valid
# Args:
#   $1 - Cache key (e.g., "compile-patra-ingest")
#   $2 - Files hash
# Returns: 0 if cache hit, 1 if cache miss
check_cache() {
  local cache_key="$1"
  local files_hash="$2"
  local cache_file="$CACHE_DIR/${cache_key}.cache"

  if [[ -f "$cache_file" ]]; then
    local cached_hash
    cached_hash=$(cat "$cache_file")
    if [[ "$cached_hash" == "$files_hash" ]]; then
      echo "[CACHE HIT] $cache_key" >&2
      return 0
    fi
  fi

  echo "[CACHE MISS] $cache_key" >&2
  return 1
}

# Store result in cache
# Args:
#   $1 - Cache key
#   $2 - Files hash
save_cache() {
  local cache_key="$1"
  local files_hash="$2"
  local cache_file="$CACHE_DIR/${cache_key}.cache"

  # Ensure cache directory exists
  mkdir -p "$(dirname "$cache_file")"
  echo "$files_hash" > "$cache_file"
  echo "[CACHE SAVED] $cache_key" >&2
}

# Clear all cache (useful after Maven plugin updates)
clear_cache() {
  rm -rf "$CACHE_DIR"
  echo "[CACHE CLEARED] All cached results removed" >&2
}

# ============================================================================
# MODULE DETECTION
# ============================================================================

# Find the Maven module (directory with pom.xml) for a given file.
# Returns:
#   - "." if the file belongs to the root aggregator module
#   - Relative path from repo root to the module directory
#   - Empty string if no module found
find_module_for_file() {
  local f="$1"
  local root_dir="$2"

  # Skip deleted files or paths outside repo
  [[ -e "$f" || -L "$f" ]] || return 0

  local dir
  dir=$(dirname -- "$f")

  # Walk up directory tree until we find a pom.xml or reach repo root
  while true; do
    if [[ -f "$dir/pom.xml" ]]; then
      # If the module is the repo root, return '.'
      if [[ "$dir" == "$root_dir" ]]; then
        printf '.\n'
      else
        # Calculate relative path from root to module
        # Try GNU realpath first (most Linux), fallback to Python
        if command -v realpath >/dev/null 2>&1; then
          realpath --relative-to="$root_dir" "$dir" 2>/dev/null || \
            python3 -c "import os; print(os.path.relpath('$dir', '$root_dir'))"
        else
          python3 -c "import os; print(os.path.relpath('$dir', '$root_dir'))"
        fi
      fi
      return 0
    fi

    # Stop if we've reached filesystem root or repo root
    [[ "$dir" == "/" || "$dir" == "." || "$dir" == "$root_dir" ]] && break
    dir=$(dirname -- "$dir")
  done
}

# Collect affected Maven modules from a list of changed files (SERIAL version).
# Reads file paths from stdin, writes unique module paths to stdout.
# Args:
#   $1 - Root directory of the repository
collect_affected_modules() {
  local root_dir="$1"
  local modules_tmp
  modules_tmp=$(mktemp)

  # Ensure cleanup on function exit
  trap 'rm -f "$modules_tmp"' RETURN

  while IFS= read -r f; do
    local mod
    mod=$(find_module_for_file "$f" "$root_dir" || true)
    [[ -z "$mod" ]] && continue
    printf '%s\n' "$mod" >>"$modules_tmp"
  done

  # Sort and deduplicate
  sort -u "$modules_tmp"
  rm -f "$modules_tmp"
}

# Collect affected Maven modules with PARALLEL processing (faster for many files).
# Reads file paths from stdin, writes unique module paths to stdout.
# Args:
#   $1 - Root directory of the repository
#   $2 - Max parallel jobs (default: number of CPUs)
collect_affected_modules_parallel() {
  local root_dir="$1"
  local max_jobs="${2:-$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)}"
  local modules_tmp
  modules_tmp=$(mktemp)

  # Ensure cleanup on function exit
  trap 'rm -f "$modules_tmp"' RETURN

  # Export function for xargs to use
  export -f find_module_for_file
  export root_dir

  # Process files in parallel
  xargs -P "$max_jobs" -I {} bash -c 'find_module_for_file "{}" "$root_dir"' 2>/dev/null >> "$modules_tmp"

  # Sort and deduplicate
  sort -u "$modules_tmp"
  rm -f "$modules_tmp"
}

# ============================================================================
# MAVEN EXECUTION
# ============================================================================

# Run Maven command with appropriate flags for the detected modules.
# Args:
#   $1 - Root directory
#   $2 - Modules temp file path
#   $3+ - Maven goals and arguments
run_maven_on_modules() {
  local root_dir="$1"
  local modules_file="$2"
  shift 2

  cd "$root_dir" || {
    echo "[ERROR] Failed to cd to $root_dir" >&2
    return 1
  }

  if [[ ! -s "$modules_file" ]]; then
    return 0  # No modules affected
  fi

  # If root aggregator is affected, run on entire project
  if grep -qxF '.' "$modules_file"; then
    echo "[maven] Running: ./mvnw -q -T1C $*"
    ./mvnw -q -T1C "$@" || {
      echo "❌ Maven command failed. Run './mvnw $*' manually to see full errors." >&2
      return 1
    }
    return 0
  fi

  # Otherwise, run on specific modules with -am (also make dependencies)
  local module_list
  module_list=$(paste -sd, "$modules_file")

  echo "[maven] Running: ./mvnw -q -T1C -pl ${module_list} -am $*"
  ./mvnw -q -T1C -pl "${module_list}" -am "$@" || {
    echo "❌ Maven command failed. Run './mvnw -pl ${module_list} -am $*' manually to see full errors." >&2
    return 1
  }
}

# Run Maven command with caching support
# Args:
#   $1 - Cache key prefix (e.g., "compile", "test")
#   $2 - Root directory
#   $3 - Modules temp file path
#   $4 - Files temp file path (for hash computation)
#   $5+ - Maven goals and arguments
run_maven_with_cache() {
  local cache_key_prefix="$1"
  local root_dir="$2"
  local modules_file="$3"
  local files_file="$4"
  shift 4

  # Initialize cache
  init_cache

  # Compute hash of changed files
  local files_hash
  files_hash=$(compute_files_hash "$root_dir" < "$files_file")

  # Build cache key from modules
  local cache_key="${cache_key_prefix}"
  if [[ -s "$modules_file" ]]; then
    local modules_str
    modules_str=$(tr '\n' '-' < "$modules_file" | sed 's/-$//')
    cache_key="${cache_key_prefix}-${modules_str}"
  fi

  # Check cache
  if check_cache "$cache_key" "$files_hash"; then
    echo "✓ Skipping Maven execution (cached result valid)"
    return 0
  fi

  # Run Maven
  if run_maven_on_modules "$root_dir" "$modules_file" "$@"; then
    # Save to cache on success
    save_cache "$cache_key" "$files_hash"
    return 0
  else
    return 1
  fi
}
