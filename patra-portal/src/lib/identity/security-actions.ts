"use server";

import { z } from "zod";
import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import { writeSessionCookie } from "@/lib/auth/session-cookie";
import type { CurrentUser, FieldError } from "@/lib/auth/types";
import {
  type CommonFailure,
  isReauthRequired,
  REAUTH_REQUIRED,
  type ReauthRequired,
  settleFailure,
  unexpectedProblem,
} from "@/lib/identity/mutation-failure";
import { callIdentity } from "@/lib/identity/request";
import { userSchema } from "@/lib/identity/user-schema";

const UNAVAILABLE = { ok: false, reason: "unavailable" } as const;
const PASSWORD_PATH = "/me/password";

/// 改密码的结果。不带文案，卡片按 `reason` 与字段的原因码选文案。
export type ChangePasswordResult =
  | { ok: true; user: CurrentUser }
  | { ok: false; reason: "wrong-password" }
  | { ok: false; reason: "invalid-fields"; fieldErrors: FieldError[] }
  | { ok: false; reason: "throttled"; retryAfterSeconds: number | null }
  | CommonFailure;

/// 改密码的 200 总带新令牌
const passwordChangedSchema = z.object({ sessionToken: z.string().min(1), user: userSchema });

/// 改密码（`PUT /me/password`，每次都要当前密码，不走再认证）。200 换掉 Cookie 里的令牌（本机保留登录，
/// 其他会话全部撤销）；401 `PASSWORD_MISMATCH` 是当前密码错（会话仍有效）；422 字段错误原样带回；429 与登录
/// 共用失败限制；冷静期 409 由 `settleFailure` 归类。
export async function changePassword(input: {
  currentPassword: string;
  newPassword: string;
}): Promise<ChangePasswordResult> {
  const endpoint = `/patra-identity${PASSWORD_PATH}`;
  const reply = await callIdentity(PASSWORD_PATH, {
    method: "PUT",
    json: { currentPassword: input.currentPassword, newPassword: input.newPassword },
  });
  if (reply.kind === "ok") {
    const parsed =
      reply.res.status === 200 ? passwordChangedSchema.safeParse(reply.body) : undefined;
    if (!parsed?.success) {
      logUnexpectedResponse(endpoint, reply.res.status, reply.body);
      return UNAVAILABLE;
    }
    await writeSessionCookie(parsed.data.sessionToken);
    return { ok: true, user: parsed.data.user };
  }
  if (reply.kind === "problem") {
    const { problem } = reply;
    if (problem.status === 401 && problem.traits.has("PASSWORD_MISMATCH")) {
      return { ok: false, reason: "wrong-password" };
    }
    if (problem.status === 422 && problem.errors.length > 0) {
      return { ok: false, reason: "invalid-fields", fieldErrors: problem.errors };
    }
    if (problem.status === 429) {
      return { ok: false, reason: "throttled", retryAfterSeconds: problem.retryAfterSeconds };
    }
  }
  const settled = await settleFailure(reply);
  return "failure" in settled ? settled.failure : unexpectedProblem(endpoint, settled.problem);
}

/// 踢设备的结果
export type RevokeResult = { ok: true; revokedSessions: number } | ReauthRequired | CommonFailure;

const revokedSchema = z.object({ revokedSessions: z.number().int().nonnegative() });

/// 踢设备的共同部分：DELETE 一个会话路径，200 带被踢数量；403 交给 `withReauth`；其余（含 409
/// `CURRENT_SESSION`：本机行不显示「退出」，正常不会出现）按 `unavailable`
async function revoke(path: string): Promise<RevokeResult> {
  const endpoint = `/patra-identity${path}`;
  const reply = await callIdentity(path, { method: "DELETE" });
  if (reply.kind === "ok") {
    const parsed = reply.res.status === 200 ? revokedSchema.safeParse(reply.body) : undefined;
    if (!parsed?.success) {
      logUnexpectedResponse(endpoint, reply.res.status, reply.body);
      return UNAVAILABLE;
    }
    return { ok: true, revokedSessions: parsed.data.revokedSessions };
  }
  const settled = await settleFailure(reply);
  if ("failure" in settled) {
    return settled.failure;
  }
  return isReauthRequired(settled.problem)
    ? REAUTH_REQUIRED
    : unexpectedProblem(endpoint, settled.problem);
}

/// 踢掉一台设备（`DELETE /me/sessions/{id}`，要求再认证；已经不在也是 200、数量 0）。`id` 只收数字串，
/// 其余不请求，防止拼出别的路径
export async function revokeSession(id: string): Promise<RevokeResult> {
  if (!/^\d{1,19}$/.test(id)) {
    return UNAVAILABLE;
  }
  return revoke(`/me/sessions/${id}`);
}

/// 踢掉除本机外的全部设备（`DELETE /me/sessions/others`，要求再认证）
export async function revokeOtherSessions(): Promise<RevokeResult> {
  return revoke("/me/sessions/others");
}
