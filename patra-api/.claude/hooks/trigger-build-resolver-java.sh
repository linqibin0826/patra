#!/bin/bash

# trigger-build-resolver-java.sh
# Purpose: Automatically suggest using auto-error-resolver agent when Maven compilation fails
# Triggers on: Stop event (after maven-compile-check.sh)
# Dependencies: maven-compile-check.sh must run first

set -e

# Colors for output
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Check if there's a compilation error indicator
# This script should run after maven-compile-check.sh
# If previous hook failed (compilation errors), suggest using the agent

# Check if we're in a Java project
PROJECT_ROOT="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"

if [ ! -f "$PROJECT_ROOT/pom.xml" ]; then
    # Not a Maven project, exit silently
    exit 0
fi

# Look for recent compilation errors in the session
# This is a simplified check - in practice, you might want to check hook exit status
if [ -f "$PROJECT_ROOT/.claude/hooks/.last-compile-failed" ]; then
    echo ""
    echo -e "${YELLOW}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${YELLOW}║ 🤖 Auto-Error-Resolver Agent Available                   ║${NC}"
    echo -e "${YELLOW}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${CYAN}Would you like me to use the auto-error-resolver agent to fix these compilation errors?${NC}"
    echo ""
    echo "The agent will:"
    echo "  1. Analyze compilation errors"
    echo "  2. Identify root causes"
    echo "  3. Apply fixes automatically"
    echo "  4. Re-run compilation to verify"
    echo ""

    # Clean up the marker file
    rm -f "$PROJECT_ROOT/.claude/hooks/.last-compile-failed"
fi

exit 0
