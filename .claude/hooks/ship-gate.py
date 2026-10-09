#!/usr/bin/env python3
"""交付闸门：推送 / 开 PR / 合并必须由用户当轮明确授权。

bypass 权限模式会跳过 permissions.ask，但 hook 照常执行、deny 必定生效，所以用 hook 兜底。

- UserPromptSubmit：用户消息里明确说了"合并 / 开 PR / 推送 / /ship"等 → 给本会话记授权；
  否则清掉授权。授权只活到用户下一条消息，不会延续到后面的 PR。
  运行环境自动发来的通知（<task-notification>、<ci-monitor-event>、<system-reminder>）不是用户消息：
  整条都是通知时不记授权也不清授权；用户消息附带的提醒块不参与匹配；
  通知标签和别的文字混在一起时认不准来源，不记授权。
- PreToolUse：git push、gh pr create/merge/ready、合并 API、auto-merge 在没有授权时 deny。
"""

import json
import re
import sys
from pathlib import Path

STATE_DIR = Path(__file__).resolve().parent / "state" / "ship-approval"

APPROVE = re.compile(r"合并|开\s*PR|提\s*PR|推送|推上去|推吧|/ship|\bpush\b|\bmerge\b", re.I)
NEGATE = re.compile(r"(别|不要|先不|暂不|不用|无需|不需要|不急着?)\s*(合并|开|提|推|push|merge)", re.I)

# 运行环境自动发来的内容（子代理完成 / Monitor 事件、CI 事件、系统提醒）也走 UserPromptSubmit，但不是用户写的。
SYSTEM_BLOCK = re.compile(r"<(task-notification|ci-monitor-event|system-reminder)>.*?</\1>", re.S)
REMINDER_BLOCK = re.compile(r"<system-reminder>.*?</system-reminder>", re.S)
NOTICE_TAG = re.compile(r"<(task-notification|ci-monitor-event)\b")
SYSTEM_PREFIX = "[SYSTEM NOTIFICATION - NOT USER INPUT]"

GATED_COMMAND = re.compile(
    r"\bgit\s+push\b"
    r"|\bgh\s+pr\s+(create|merge|ready)\b"
    r"|\bgh\s+api\b.*pulls/\d+/merge"
)
GATED_TOOLS = {"mcp__ccd_pr__set_auto_merge"}


def approval_file(session_id: str) -> Path:
    return STATE_DIR / re.sub(r"[^\w-]", "_", session_id or "unknown")


def is_system_notice(prompt: str) -> bool:
    if prompt.lstrip().startswith(SYSTEM_PREFIX):
        return True
    return bool(prompt.strip()) and not SYSTEM_BLOCK.sub("", prompt).strip()


def on_prompt(data: dict) -> None:
    prompt = data.get("prompt", "")
    if is_system_notice(prompt):
        return  # 整条都是系统通知：不记授权，也不动用户此前给的授权
    marker = approval_file(data.get("session_id", ""))
    # 用户消息附带的提醒块不是用户写的；换成 \0 而不是删掉，免得块两侧的字拼成新的授权词。
    user_text = REMINDER_BLOCK.sub("\0", prompt)
    # 通知标签和别的文字混在一起时认不准来源，按没授权处理。
    if not NOTICE_TAG.search(user_text) and APPROVE.search(user_text) and not NEGATE.search(prompt):
        STATE_DIR.mkdir(parents=True, exist_ok=True)
        marker.write_text(prompt[:200], encoding="utf-8")
        print("[ship-gate] 用户本条消息授权了推送 / 开 PR / 合并，授权到用户下一条消息为止。")
    else:
        marker.unlink(missing_ok=True)


def on_tool(data: dict) -> None:
    tool = data.get("tool_name", "")
    command = (data.get("tool_input") or {}).get("command", "")
    gated = tool in GATED_TOOLS or (tool == "Bash" and GATED_COMMAND.search(command))
    if not gated or approval_file(data.get("session_id", "")).exists():
        return
    print(
        json.dumps(
            {
                "hookSpecificOutput": {
                    "hookEventName": "PreToolUse",
                    "permissionDecision": "deny",
                    "permissionDecisionReason": (
                        "ship-gate：推送 / 开 PR / 合并需要用户本轮明确授权。"
                        "先停在本地，把改动和验证方式交给用户验收；"
                        "用户说『开 PR』『合并』『推吧』或 /ship 之后再执行。"
                    ),
                }
            },
            ensure_ascii=False,
        )
    )


def main() -> None:
    data = json.load(sys.stdin)
    event = data.get("hook_event_name")
    if event == "UserPromptSubmit":
        on_prompt(data)
    elif event == "PreToolUse":
        on_tool(data)


if __name__ == "__main__":
    main()
