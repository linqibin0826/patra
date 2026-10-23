"use server";

import { z } from "zod";
import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import { clearSessionCookie } from "@/lib/auth/session-cookie";
import { isSessionRejected } from "@/lib/identity/problem";
import { callIdentity } from "@/lib/identity/request";

const RESEND_PATH = "/me/email/verification/resend";

/// 重发验证邮件的结果。不带文案，按钮按 `reason` 选文案。
export type ResendResult =
  | { ok: true; nextResendAt: string }
  | { ok: false; reason: "throttled"; retryAfterSeconds: number | null }
  | { ok: false; reason: "already-verified" | "pending-deletion" | "signed-out" | "unavailable" };

const sentSchema = z.object({ nextResendAt: z.string().min(1) });

/// 重发验证邮件（`POST /me/email/verification/resend`，无请求体）。202 带下次可重发的时间；429 带等待
/// 秒数；409 按 `traits` 分已验证与冷静期；会话失效时清掉会话 Cookie。其余都是 `unavailable`。
export async function resendVerificationEmail(): Promise<ResendResult> {
  const reply = await callIdentity(RESEND_PATH, { method: "POST" });
  switch (reply.kind) {
    case "no-session":
      return { ok: false, reason: "signed-out" };
    case "error":
      return { ok: false, reason: "unavailable" };
    case "ok": {
      const parsed = reply.res.status === 202 ? sentSchema.safeParse(reply.body) : undefined;
      if (!parsed?.success) {
        logUnexpectedResponse(`/patra-identity${RESEND_PATH}`, reply.res.status, reply.body);
        return { ok: false, reason: "unavailable" };
      }
      return { ok: true, nextResendAt: parsed.data.nextResendAt };
    }
    case "problem": {
      const { problem } = reply;
      if (isSessionRejected(problem)) {
        await clearSessionCookie();
        return { ok: false, reason: "signed-out" };
      }
      if (problem.status === 429) {
        return { ok: false, reason: "throttled", retryAfterSeconds: problem.retryAfterSeconds };
      }
      if (problem.status === 409 && problem.traits.has("PENDING_DELETION")) {
        return { ok: false, reason: "pending-deletion" };
      }
      if (problem.status === 409 && problem.traits.has("EMAIL_ALREADY_VERIFIED")) {
        return { ok: false, reason: "already-verified" };
      }
      logUnexpectedResponse(`/patra-identity${RESEND_PATH}`, problem.status, {
        code: problem.code,
      });
      return { ok: false, reason: "unavailable" };
    }
  }
}
