import "server-only";
import { z } from "zod";
import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import { writeSessionCookie } from "@/lib/auth/session-cookie";
import type { CurrentUser } from "@/lib/auth/types";
import {
  type CommonFailure,
  settleFailure,
  unexpectedProblem,
} from "@/lib/identity/mutation-failure";
import { callIdentity } from "@/lib/identity/request";
import { userSchema } from "@/lib/identity/user-schema";

const REAUTH_PATH = "/auth/reauth";
const REAUTH_ENDPOINT = `/patra-identity${REAUTH_PATH}`;

/// 再认证的结果。不带文案，对话框按 `reason` 选文案。
export type ReauthResult =
  | { ok: true; user: CurrentUser }
  | { ok: false; reason: "wrong-password" }
  | { ok: false; reason: "throttled"; retryAfterSeconds: number | null }
  | CommonFailure;

/// `sessionToken` 可能为 null：上次密码验证距今不足 10 秒时服务端不换令牌（PAP-98）
const reauthenticatedSchema = z.object({
  sessionToken: z.string().min(1).nullable(),
  user: userSchema,
});

/// 再认证（`POST /auth/reauth`）：验证密码，刷新本会话的再认证时间。只由 Route Handler `POST /api/auth/reauth`
/// 调用，不做成 Server Action：Server Action 里写 Cookie 会让 Next 重新渲染当前页，这次重新渲染可能晚于随后重试
/// 的原操作才到——注销成功后会话已被撤销，设置页的守卫就把结果屏换成了登录页（spec §18.3）。
/// - 200：令牌非 null 时换掉 Cookie 里的令牌（旧令牌已失效），为 null 时保持现有 Cookie（两个标签页并发再认证，
///   先换出的令牌保持有效）
/// - 401 含 `PASSWORD_MISMATCH`、422 `password`：`wrong-password`（会话仍有效，不清 Cookie）
/// - 429：`throttled`（与登录共用失败限制）
/// - 其余 401：会话没了，清 Cookie，`signed-out`；请求抛错、其他响应：`unavailable`
export async function reauthenticate(password: string): Promise<ReauthResult> {
  const reply = await callIdentity(REAUTH_PATH, { method: "POST", json: { password } });
  if (reply.kind === "ok") {
    const parsed =
      reply.res.status === 200 ? reauthenticatedSchema.safeParse(reply.body) : undefined;
    if (!parsed?.success) {
      logUnexpectedResponse(REAUTH_ENDPOINT, reply.res.status, reply.body);
      return { ok: false, reason: "unavailable" };
    }
    if (parsed.data.sessionToken !== null) {
      await writeSessionCookie(parsed.data.sessionToken);
    }
    return { ok: true, user: parsed.data.user };
  }
  if (reply.kind === "problem") {
    const { problem } = reply;
    if (
      (problem.status === 401 && problem.traits.has("PASSWORD_MISMATCH")) ||
      (problem.status === 422 && problem.errors.some((error) => error.field === "password"))
    ) {
      return { ok: false, reason: "wrong-password" };
    }
    if (problem.status === 429) {
      return { ok: false, reason: "throttled", retryAfterSeconds: problem.retryAfterSeconds };
    }
  }
  const settled = await settleFailure(reply);
  return "failure" in settled
    ? settled.failure
    : unexpectedProblem(REAUTH_ENDPOINT, settled.problem);
}
