"use server";

import { z } from "zod";
import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import { writeSessionCookie } from "@/lib/auth/session-cookie";
import type { CurrentUser, FieldError } from "@/lib/auth/types";
import {
  type CommonFailure,
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
