import "server-only";
import { type PendingEmailChange, pendingEmailChangeSchema } from "@/lib/identity/account-schema";
import { isSessionRejected } from "@/lib/identity/problem";
import { callIdentity } from "@/lib/identity/request";

/// RSC 里读待确认的改邮箱（`GET /me/email/change`）：200 返回它、204 没有是 null；没有会话或会话失效是
/// `signed-out`（页面跳登录页，RSC 里不能清 Cookie）；其余抛错交给错误页。
export async function fetchPendingEmailChange(): Promise<PendingEmailChange | null | "signed-out"> {
  const reply = await callIdentity("/me/email/change");
  if (
    reply.kind === "no-session" ||
    (reply.kind === "problem" && isSessionRejected(reply.problem))
  ) {
    return "signed-out";
  }
  if (reply.kind === "ok" && reply.res.status === 204) {
    return null;
  }
  if (reply.kind === "ok" && reply.res.status === 200) {
    const parsed = pendingEmailChangeSchema.safeParse(reply.body);
    if (parsed.success) {
      return parsed.data;
    }
  }
  throw new Error("读取待确认的改邮箱失败");
}
