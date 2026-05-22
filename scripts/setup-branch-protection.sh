#!/usr/bin/env bash
# main 分支保护规则一次性配置脚本（幂等，可重复运行）
# 用法: ./scripts/setup-branch-protection.sh
# 依赖: gh CLI 已登录、对仓库有 admin 权限

set -euo pipefail

OWNER_REPO="linqibin0826/patra"
BRANCH="main"

echo "→ 配置 ${OWNER_REPO} 分支 ${BRANCH} 的保护规则..."

gh api \
  --method PUT \
  -H "Accept: application/vnd.github+json" \
  "/repos/${OWNER_REPO}/branches/${BRANCH}/protection" \
  --input - <<'JSON'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["required-check"]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": null,
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "block_creations": false,
  "required_conversation_resolution": true,
  "lock_branch": false,
  "allow_fork_syncing": false
}
JSON

echo "✓ 完成。当前规则:"
gh api "/repos/${OWNER_REPO}/branches/${BRANCH}/protection" \
  | jq '{required_status_checks, enforce_admins, required_linear_history, allow_force_pushes, allow_deletions, required_conversation_resolution}'
