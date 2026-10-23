import "server-only";
import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import { clearSessionCookie } from "@/lib/auth/session-cookie";
import { isSessionRejected, type Problem } from "@/lib/identity/problem";
import type { IdentityReply } from "@/lib/identity/request";

/// 各写操作都一样处理的失败（结果里的 `reason`）
export type CommonFailure = {
  ok: false;
  reason: "signed-out" | "pending-deletion" | "unavailable";
};

/// 先归类几种写操作都一样的失败，其余的 ProblemDetail 交回调用方按自己的接口判：
/// - 没有会话：`signed-out`
/// - 会话失效（见 `isSessionRejected`）：清掉会话 Cookie，`signed-out`
/// - 冷静期（409 带 `PENDING_DELETION`）：`pending-deletion`
/// - 请求抛错：`unavailable`
///
/// 会清 Cookie，只能在 Server Action 与 Route Handler 里用，RSC 里不行。
export async function settleFailure(
  reply: Exclude<IdentityReply, { kind: "ok" }>,
): Promise<{ failure: CommonFailure } | { problem: Problem }> {
  if (reply.kind === "no-session") {
    return { failure: { ok: false, reason: "signed-out" } };
  }
  if (reply.kind === "error") {
    return { failure: { ok: false, reason: "unavailable" } };
  }
  const { problem } = reply;
  if (isSessionRejected(problem)) {
    await clearSessionCookie();
    return { failure: { ok: false, reason: "signed-out" } };
  }
  if (problem.status === 409 && problem.traits.has("PENDING_DELETION")) {
    return { failure: { ok: false, reason: "pending-deletion" } };
  }
  return { problem };
}

/// 调用方也认不出的问题：记日志（只记接口、状态码与 `code`），按 `unavailable` 处理
export function unexpectedProblem(endpoint: string, problem: Problem): CommonFailure {
  logUnexpectedResponse(endpoint, problem.status, { code: problem.code });
  return { ok: false, reason: "unavailable" };
}

/// 需要再认证（spec §6.1）：要求 10 分钟内输过密码的操作超出窗口时的结果。客户端拿到它就弹再认证对话框，
/// 验证后重试原操作（`useReauth().withReauth`）。只出现在改邮箱、踢设备、发起注销这几个 Action 里，所以不并进
/// `CommonFailure`：否则不需要再认证的操作也得为它写分支。
export type ReauthRequired = { ok: false; reason: "reauth-required" };

/// `ReauthRequired` 的唯一实例
export const REAUTH_REQUIRED: ReauthRequired = { ok: false, reason: "reauth-required" };

/// 403 且 `traits` 含 `REAUTH_REQUIRED`
export function isReauthRequired(problem: Problem): boolean {
  return problem.status === 403 && problem.traits.has("REAUTH_REQUIRED");
}
