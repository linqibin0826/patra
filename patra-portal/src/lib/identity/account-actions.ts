"use server";

import { z } from "zod";
import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import type { CurrentUser } from "@/lib/auth/types";
import { type PendingEmailChange, pendingEmailChangeSchema } from "@/lib/identity/account-schema";
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

const DELETION_PATH = "/me/deletion";
const UNAVAILABLE = { ok: false, reason: "unavailable" } as const;

/// 取消注销（`DELETE /me/deletion`，不要求再认证）。200 带与 `/auth/me` 同构的用户（重复取消也 200）；
/// 会话失效清 Cookie 回 `signed-out`；其余都是 `unavailable`。
export async function cancelDeletion(): Promise<{ ok: true; user: CurrentUser } | CommonFailure> {
  const reply = await callIdentity(DELETION_PATH, { method: "DELETE" });
  if (reply.kind === "ok") {
    const parsed = reply.res.status === 200 ? userSchema.safeParse(reply.body) : undefined;
    if (!parsed?.success) {
      logUnexpectedResponse(`/patra-identity${DELETION_PATH}`, reply.res.status, reply.body);
      return UNAVAILABLE;
    }
    return { ok: true, user: parsed.data };
  }
  const settled = await settleFailure(reply);
  return "failure" in settled
    ? settled.failure
    : unexpectedProblem(`/patra-identity${DELETION_PATH}`, settled.problem);
}

const USERNAME_PATH = "/me/username";

/// 改用户名的结果。不带文案，卡片按 `reason` 与原因码选文案。
export type ChangeUsernameResult =
  | { ok: true; username: string; nextUsernameChangeAt: string }
  | { ok: false; reason: "invalid"; code: string | null }
  | { ok: false; reason: "taken" | "cooldown" }
  | CommonFailure;

const usernameChangedSchema = z.object({
  username: z.string().min(1),
  nextUsernameChangeAt: z.string().min(1),
});

/// 改用户名（`PATCH /me/username`，不要求再认证）。422 带原因码（含 `UNCHANGED`）；409 按 `errors[]` 是被占用、
/// 按 `traits` 是冷却（`USERNAME_CHANGE_COOLDOWN`）或冷静期（由 `settleFailure` 归类）。
export async function changeUsername(username: string): Promise<ChangeUsernameResult> {
  const endpoint = `/patra-identity${USERNAME_PATH}`;
  const reply = await callIdentity(USERNAME_PATH, { method: "PATCH", json: { username } });
  if (reply.kind === "ok") {
    const parsed =
      reply.res.status === 200 ? usernameChangedSchema.safeParse(reply.body) : undefined;
    if (!parsed?.success) {
      logUnexpectedResponse(endpoint, reply.res.status, reply.body);
      return UNAVAILABLE;
    }
    return { ok: true, ...parsed.data };
  }
  const settled = await settleFailure(reply);
  if ("failure" in settled) {
    return settled.failure;
  }
  const { problem } = settled;
  const fieldError = problem.errors.find((error) => error.field === "username");
  if (problem.status === 422 && fieldError) {
    return { ok: false, reason: "invalid", code: fieldError.code };
  }
  if (problem.status === 409 && fieldError?.code === "ALREADY_TAKEN") {
    return { ok: false, reason: "taken" };
  }
  if (problem.status === 409 && problem.traits.has("USERNAME_CHANGE_COOLDOWN")) {
    return { ok: false, reason: "cooldown" };
  }
  return unexpectedProblem(endpoint, problem);
}

const EMAIL_CHANGE_PATH = "/me/email/change";

/// 申请改邮箱的结果。不带文案，卡片按 `reason` 与原因码选文案。
export type RequestEmailChangeResult =
  | { ok: true; pending: PendingEmailChange }
  | { ok: false; reason: "invalid"; code: string | null }
  | { ok: false; reason: "taken" | "pending-exists" | "throttled" }
  | ReauthRequired
  | CommonFailure;

/// 申请改邮箱（`POST /me/email/change`，要求再认证）：202 返回待确认；403 `REAUTH_REQUIRED` 交给
/// `withReauth`；409 按 `traits` 是已有待确认（`EMAIL_CHANGE_PENDING`）、按 `errors[]` 是新邮箱已被使用；
/// 422 `newEmail` 带原因码（含 `UNCHANGED`）；429 今天的次数用完。
export async function requestEmailChange(newEmail: string): Promise<RequestEmailChangeResult> {
  const endpoint = `/patra-identity${EMAIL_CHANGE_PATH}`;
  const reply = await callIdentity(EMAIL_CHANGE_PATH, { method: "POST", json: { newEmail } });
  if (reply.kind === "ok") {
    const parsed =
      reply.res.status === 202 ? pendingEmailChangeSchema.safeParse(reply.body) : undefined;
    if (!parsed?.success) {
      logUnexpectedResponse(endpoint, reply.res.status, reply.body);
      return UNAVAILABLE;
    }
    return { ok: true, pending: parsed.data };
  }
  const settled = await settleFailure(reply);
  if ("failure" in settled) {
    return settled.failure;
  }
  const { problem } = settled;
  if (isReauthRequired(problem)) {
    return REAUTH_REQUIRED;
  }
  if (problem.status === 409 && problem.traits.has("EMAIL_CHANGE_PENDING")) {
    return { ok: false, reason: "pending-exists" };
  }
  if (problem.status === 409 && problem.errors.some((error) => error.field === "email")) {
    return { ok: false, reason: "taken" };
  }
  const fieldError = problem.errors.find((error) => error.field === "newEmail");
  if (problem.status === 422 && fieldError) {
    return { ok: false, reason: "invalid", code: fieldError.code };
  }
  if (problem.status === 429) {
    return { ok: false, reason: "throttled" };
  }
  return unexpectedProblem(endpoint, problem);
}

/// 取消待确认的改邮箱（`DELETE /me/email/change`，没有待确认也 204）
export async function cancelEmailChange(): Promise<{ ok: true } | CommonFailure> {
  const reply = await callIdentity(EMAIL_CHANGE_PATH, { method: "DELETE" });
  if (reply.kind === "ok") {
    return { ok: true };
  }
  const settled = await settleFailure(reply);
  return "failure" in settled
    ? settled.failure
    : unexpectedProblem(`/patra-identity${EMAIL_CHANGE_PATH}`, settled.problem);
}
