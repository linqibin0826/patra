#!/bin/bash

# maven-compile-check.sh
# Purpose: Quick Maven compilation check for Papertrace multi-module project
# Triggers on: Stop event (only if Java files were modified)
# Usage: Runs mvn compile with multi-threading to quickly detect compilation errors

set -eo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Navigate to project root (assumes we're in .claude/hooks)
PROJECT_ROOT="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
cd "$PROJECT_ROOT" || exit 1

# Check if Java files were modified in this session
MARKER_FILE="$PROJECT_ROOT/.claude/hooks/.java-files-modified"
if [ ! -f "$MARKER_FILE" ]; then
    echo -e "${BLUE}ℹ️  No Java file changes detected, skipping Maven compilation check${NC}"
    exit 0
fi

# Remove the marker file (will be recreated if Java files are modified again)
rm -f "$MARKER_FILE"

echo "🔨 Running Maven compilation check..."

# Check if pom.xml exists
if [ ! -f "pom.xml" ]; then
    echo -e "${RED}❌ Error: No pom.xml found in project root${NC}"
    exit 1
fi

# Create temp file for compilation output
TEMP_OUTPUT=$(mktemp)
trap 'rm -f "$TEMP_OUTPUT"' EXIT

# Run Maven compile with multi-threading
# -T 2C: Use 2 threads per CPU core (optimal based on performance testing)
# -q: Quiet mode (only errors)
# -DskipTests: Skip test compilation and execution
echo "Running: mvn -T 2C compile -q -DskipTests"

if mvn -T 2C compile -q -DskipTests 2>&1 | tee "$TEMP_OUTPUT"; then
    echo -e "${GREEN}✅ Maven compilation successful${NC}"
    exit 0
else
    echo -e "${RED}❌ Maven compilation failed${NC}"
    echo ""
    echo "=== Compilation Errors Summary ==="

    # Extract and display compilation errors
    grep -E "\[ERROR\]|error:|cannot find symbol|package .* does not exist" "$TEMP_OUTPUT" | head -20

    echo ""
    echo "=== Failed Modules ==="
    grep -E "BUILD FAILURE|FAILURE \[" "$TEMP_OUTPUT" | sed 's/.*\[\(.*\)\].*/  - \1/' | sort -u

    echo ""
    echo -e "${YELLOW}💡 Tip: Run 'mvn compile' for detailed error information${NC}"
    echo -e "${YELLOW}💡 Or use the auto-error-resolver agent to fix errors automatically${NC}"

    # Create marker file for trigger-build-resolver-java.sh
    mkdir -p "$PROJECT_ROOT/.claude/hooks"
    touch "$PROJECT_ROOT/.claude/hooks/.last-compile-failed"

    exit 1
fi
