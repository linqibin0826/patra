#!/bin/bash

# auto-format-java.sh
# Purpose: Automatically format Java files after Edit/Write using Google Java Format
# Triggers on: PostToolUse (after Edit/Write/MultiEdit)
# Usage: Runs fmt:format on the modified file

set -e

# Colors for output
BLUE='\033[0;34m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Read stdin to get file path from tool input
FILE_PATH=$(cat | jq -r '.tool_input.file_path // empty' 2>/dev/null)

# Check if it's a Java file
if [[ "$FILE_PATH" == *.java ]]; then
    PROJECT_ROOT="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"

    # Check if file exists and is in the project
    if [[ -f "$FILE_PATH" ]]; then
        echo -e "${BLUE}🎨 Formatting Java file: $(basename "$FILE_PATH")${NC}"

        # Run fmt:format on the specific file
        # Using -q for quiet output, only show errors
        if cd "$PROJECT_ROOT" && mvn -q fmt:format -Dformat.files="$FILE_PATH" 2>/dev/null; then
            echo -e "${GREEN}✅ Format applied successfully${NC}"
        else
            # If file-specific formatting fails, try formatting the whole module
            # This is a fallback, fmt:format works at module level
            MODULE_DIR=$(dirname "$FILE_PATH")
            while [[ "$MODULE_DIR" != "$PROJECT_ROOT" ]] && [[ ! -f "$MODULE_DIR/pom.xml" ]]; do
                MODULE_DIR=$(dirname "$MODULE_DIR")
            done

            if [[ -f "$MODULE_DIR/pom.xml" ]]; then
                echo -e "${BLUE}🔄 Formatting module: $(basename "$MODULE_DIR")${NC}"
                cd "$MODULE_DIR" && mvn -q fmt:format 2>/dev/null || true
            fi
        fi
    fi
fi

# Non-blocking hook: always exit successfully
exit 0
