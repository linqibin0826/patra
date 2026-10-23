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

/// 落地页提交令牌的结果：成功带邮箱；令牌不可用（422 `token`）；地址已被别人注册（409，只有确认 / 撤销改邮箱）
export type TokenResult =
  | { ok: true; email: string }
  | { ok: false; reason: "invalid" | "taken" }
  | Unavailable;

const emailSchema = z.object({ email: z.string().min(1) });

/// 落地页的共同部分：POST `{ token }`，200 读邮箱；422 `token` 是无效；409 是被占用；其余记日志后 `unavailable`
async function consumeToken(path: string, token: string): Promise<TokenResult> {
  const endpoint = `/patra-identity${path}`;
  const reply = await callIdentity(path, { method: "POST", json: { token } }, { auth: "optional" });
  if (reply.kind === "ok") {
    const parsed = reply.res.status === 200 ? emailSchema.safeParse(reply.body) : undefined;
    if (!parsed?.success) {
      logUnexpectedResponse(endpoint, reply.res.status, reply.body);
      return UNAVAILABLE;
    }
    return { ok: true, email: parsed.data.email };
  }
  if (reply.kind !== "problem") {
    return UNAVAILABLE;
  }
  const { problem } = reply;
  if (problem.status === 422 && problem.errors.some((error) => error.field === "token")) {
    return { ok: false, reason: "invalid" };
  }
  if (problem.status === 409) {
    return { ok: false, reason: "taken" };
  }
  unexpectedProblem(endpoint, problem);
  return UNAVAILABLE;
}

/// 验证邮箱（`POST /auth/email/verify`）。有会话 Cookie 就带上令牌：令牌属于当前用户时 identity 保留本机会话、
/// 撤销其余，否则撤销该用户全部会话。不会回 409，回了按 `unavailable`。
export async function verifyEmail(
  token: string,
): Promise<{ ok: true; email: string } | { ok: false; reason: "invalid" } | Unavailable> {
  const result = await consumeToken("/auth/email/verify", token);
  if (result.ok) {
    return result;
  }
  return result.reason === "invalid" ? { ok: false, reason: "invalid" } : UNAVAILABLE;
}

/// 重设密码的结果。不带文案。
export type ResetResult =
  | { ok: true; email: string }
  | { ok: false; reason: "invalid" }
  | { ok: false; reason: "invalid-password"; code: string | null }
  | { ok: false; reason: "throttled"; retryAfterSeconds: number | null }
  | Unavailable;

const RESET_PATH = "/auth/password/reset";

/// 提交新密码（`POST /auth/password/reset`）：200 带邮箱，不自动登录（全部会话已撤销）；422 `token` 是链接无效、
/// `newPassword` 带原因码（令牌没被消费，可以改了再交）；429 过于频繁。
export async function resetPassword(input: {
  token: string;
  newPassword: string;
}): Promise<ResetResult> {
  const endpoint = `/patra-identity${RESET_PATH}`;
  const reply = await callIdentity(
    RESET_PATH,
    { method: "POST", json: { token: input.token, newPassword: input.newPassword } },
    { auth: "optional" },
  );
  if (reply.kind === "ok") {
    const parsed = reply.res.status === 200 ? emailSchema.safeParse(reply.body) : undefined;
    if (!parsed?.success) {
      logUnexpectedResponse(endpoint, reply.res.status, reply.body);
      return UNAVAILABLE;
    }
    return { ok: true, email: parsed.data.email };
  }
  if (reply.kind !== "problem") {
    return UNAVAILABLE;
  }
  const { problem } = reply;
  if (problem.status === 422 && problem.errors.some((error) => error.field === "token")) {
    return { ok: false, reason: "invalid" };
  }
  const passwordError = problem.errors.find((error) => error.field === "newPassword");
  if (problem.status === 422 && passwordError) {
    return { ok: false, reason: "invalid-password", code: passwordError.code };
  }
  if (problem.status === 429) {
    return { ok: false, reason: "throttled", retryAfterSeconds: problem.retryAfterSeconds };
  }
  unexpectedProblem(endpoint, problem);
  return UNAVAILABLE;
}

/// 确认新邮箱（`POST /auth/email/change/confirm`）：成功立即生效；409 是地址在确认前被别人注册了
export async function confirmEmailChange(token: string): Promise<TokenResult> {
  return consumeToken("/auth/email/change/confirm", token);
}

/// 撤销改邮箱（`POST /auth/email/change/revert`，「不是我」）：邮箱改回、全部会话下线、密码作废并发重设邮件；
/// 409 是旧地址已被别人注册。冷静期内也能撤销
export async function revertEmailChange(token: string): Promise<TokenResult> {
  return consumeToken("/auth/email/change/revert", token);
}
