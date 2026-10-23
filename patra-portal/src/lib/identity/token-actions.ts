"use server";

import { z } from "zod";
import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import { unexpectedProblem } from "@/lib/identity/mutation-failure";
import { callIdentity } from "@/lib/identity/request";

/// 公开的找回密码与邮件落地页接口：不要求登录，有会话 Cookie 就带上令牌（验证邮箱要靠它保留本机会话）。
/// 这些路径在网关上是公开的，不会回会话失效的 401；出现了按 `unavailable`。

const UNAVAILABLE = { ok: false, reason: "unavailable" } as const;
type Unavailable = typeof UNAVAILABLE;

const FORGOT_PATH = "/auth/password/forgot";

/// 申请重设密码的结果。不带文案。
export type ForgotResult =
  | { ok: true; email: string; nextRequestAt: string }
  | { ok: false; reason: "invalid" }
  | { ok: false; reason: "throttled"; retryAfterSeconds: number | null }
  | Unavailable;

const forgotSchema = z.object({ email: z.string().min(1), nextRequestAt: z.string().min(1) });

/// 申请重设密码（`POST /auth/password/forgot`）：注册与否一律 202（防枚举）；422 邮箱格式；429 过于频繁。
export async function requestPasswordReset(email: string): Promise<ForgotResult> {
  const endpoint = `/patra-identity${FORGOT_PATH}`;
  const reply = await callIdentity(
    FORGOT_PATH,
    { method: "POST", json: { email } },
    { auth: "optional" },
  );
  if (reply.kind === "ok") {
    const parsed = reply.res.status === 202 ? forgotSchema.safeParse(reply.body) : undefined;
    if (!parsed?.success) {
      logUnexpectedResponse(endpoint, reply.res.status, reply.body);
      return UNAVAILABLE;
    }
    return { ok: true, ...parsed.data };
  }
  if (reply.kind !== "problem") {
    return UNAVAILABLE;
  }
  const { problem } = reply;
  if (problem.status === 422 && problem.errors.some((error) => error.field === "email")) {
    return { ok: false, reason: "invalid" };
  }
  if (problem.status === 429) {
    return { ok: false, reason: "throttled", retryAfterSeconds: problem.retryAfterSeconds };
  }
  unexpectedProblem(endpoint, problem);
  return UNAVAILABLE;
}
