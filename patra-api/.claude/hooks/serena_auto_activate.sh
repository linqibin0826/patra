#!/bin/bash
# Serena Auto-Activate Hook
# Automatically activates Serena project when Claude Code session starts

# Get project directory from environment
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
PROJECT_NAME=$(basename "$PROJECT_DIR")

echo "🧠 SERENA AUTO-ACTIVATE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📁 Project: $PROJECT_NAME"
echo "📍 Path: $PROJECT_DIR"
echo ""
echo "🔄 Activating Serena project..."
echo ""
echo "Note: Serena activation happens automatically."
echo "      Use /serena:health to check status."
echo ""

# The actual activation will be done by Claude Code using the MCP tool
# This hook just provides feedback to the user
# The agent should call: mcp__serena__activate_project("$PROJECT_DIR")

exit 0
