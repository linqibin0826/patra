#!/usr/bin/env bash
# Shared utilities for Maven pre-commit hooks
# Usage: source scripts/git/lib/maven_utils.sh

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

# Collect affected Maven modules from a list of changed files.
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
