#!/usr/bin/env bash
set -euo pipefail

# Validate commit message format using Conventional Commits specification.
# https://www.conventionalcommits.org/
#
# Format: <type>(<scope>): <subject>
#
# Types: feat, fix, docs, style, refactor, test, chore, perf, ci, build, revert
# Scope: Optional (module or component name)
# Subject: Short description (50 chars max recommended)
#
# SKIP FLAGS:
#   SKIP_COMMIT_MSG=1 - Skip commit message validation
#   SKIP_HOOKS=1      - Skip all hooks

# Check for skip flags
if [[ "${SKIP_COMMIT_MSG:-0}" == "1" || "${SKIP_HOOKS:-0}" == "1" ]]; then
  echo "[commit-msg] Skipped (SKIP_COMMIT_MSG=1 or SKIP_HOOKS=1)"
  exit 0
fi

# Get commit message file from pre-commit hook
COMMIT_MSG_FILE="$1"

if [[ ! -f "$COMMIT_MSG_FILE" ]]; then
  echo "[commit-msg] ERROR: Commit message file not found: $COMMIT_MSG_FILE"
  exit 1
fi

# Read commit message (first line only)
COMMIT_MSG=$(head -n 1 "$COMMIT_MSG_FILE")

# Skip validation for merge commits, revert commits, and fixup commits
if [[ "$COMMIT_MSG" =~ ^Merge\  ]] || \
   [[ "$COMMIT_MSG" =~ ^Revert\  ]] || \
   [[ "$COMMIT_MSG" =~ ^fixup!\  ]] || \
   [[ "$COMMIT_MSG" =~ ^squash!\  ]]; then
  echo "[commit-msg] Skipping validation for special commit: ${COMMIT_MSG:0:50}..."
  exit 0
fi

# Valid types according to Conventional Commits
VALID_TYPES="feat|fix|docs|style|refactor|test|chore|perf|ci|build|revert"

# Regex pattern for Conventional Commits
# Format: type(scope): subject
# OR: type: subject
PATTERN="^($VALID_TYPES)(\([a-zA-Z0-9_-]+\))?: .+"

if [[ ! "$COMMIT_MSG" =~ $PATTERN ]]; then
  echo ""
  echo "❌ Invalid commit message format!"
  echo ""
  echo "Your commit message:"
  echo "  $COMMIT_MSG"
  echo ""
  echo "Expected format:"
  echo "  <type>(<scope>): <subject>"
  echo ""
  echo "Valid types:"
  echo "  • feat:     A new feature"
  echo "  • fix:      A bug fix"
  echo "  • docs:     Documentation changes"
  echo "  • style:    Code style changes (formatting, whitespace)"
  echo "  • refactor: Code refactoring (no functional changes)"
  echo "  • test:     Adding or updating tests"
  echo "  • chore:    Maintenance tasks (dependencies, build config)"
  echo "  • perf:     Performance improvements"
  echo "  • ci:       CI/CD changes"
  echo "  • build:    Build system changes"
  echo "  • revert:   Revert a previous commit"
  echo ""
  echo "Scope (optional):"
  echo "  • Module or component name (e.g., ingest, registry, common)"
  echo ""
  echo "Examples:"
  echo "  feat(ingest): add PubMed batch planning orchestrator"
  echo "  fix(registry): correct null pointer in Provenance lookup"
  echo "  docs: update architecture guidelines"
  echo "  refactor(app): extract validation logic to domain"
  echo "  test(domain): add unit tests for BatchPlan aggregate"
  echo ""
  echo "💡 TIP: Skip with --no-verify or SKIP_COMMIT_MSG=1"
  echo ""
  exit 1
fi

# Additional validation: Check subject length (recommended 50 chars)
SUBJECT=$(echo "$COMMIT_MSG" | sed -E "s/^($VALID_TYPES)(\([^)]+\))?: //")
SUBJECT_LENGTH=${#SUBJECT}

if [[ $SUBJECT_LENGTH -gt 72 ]]; then
  echo ""
  echo "⚠️  Warning: Commit subject is very long ($SUBJECT_LENGTH chars)"
  echo "   Recommended: 50 chars, Max: 72 chars"
  echo ""
  echo "   Consider shortening your subject line or moving details to the body."
  echo ""
  # This is a warning, not an error - allow commit to proceed
fi

# Check for imperative mood (recommended but not enforced)
if [[ "$SUBJECT" =~ ^(added|fixed|updated|changed|removed|deleted) ]]; then
  echo ""
  echo "💡 Style tip: Use imperative mood in subject"
  echo "   Instead of: 'added feature' or 'fixed bug'"
  echo "   Use:        'add feature' or 'fix bug'"
  echo ""
  # This is a suggestion, not an error - allow commit to proceed
fi

echo "[commit-msg] ✅ Commit message format validated"
exit 0
