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
