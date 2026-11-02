"use server";

import { recordingSchema } from "@/lib/analytics/history-schema";
import type { AnalyticsReply } from "@/lib/analytics/request";
import { callAnalytics } from "@/lib/analytics/request";
import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import { clearSessionCookie } from "@/lib/auth/session-cookie";
import { isSessionRejected } from "@/lib/identity/problem";
import { type CommonFailure, UNAVAILABLE } from "@/lib/interaction/action-failure";
import { isEntityId } from "@/lib/library/params";

const HISTORY = "/me/reading-history";
const SIGNED_OUT: CommonFailure = { ok: false, reason: "signed-out" };

/// 一次写操作的回复 → 成功时的响应体，或公共失败（会话被拒时清会话 Cookie；只能在 Server Action 里用）
async function settle(
  reply: AnalyticsReply,
  path: string,
): Promise<{ body: unknown } | CommonFailure> {
  if (reply.kind === "no-session") {
    return SIGNED_OUT;
  }
  if (reply.kind === "error") {
    return UNAVAILABLE;
  }
  if (reply.kind === "problem") {
    if (isSessionRejected(reply.problem)) {
      await clearSessionCookie();
      return SIGNED_OUT;
    }
    logUnexpectedResponse(`/patra-analytics${path}`, reply.problem.status, {
      code: reply.problem.code,
    });
    return UNAVAILABLE;
  }
  return { body: reply.body };
}

/// 从阅读历史删一条（阅读历史设计 §6.2）：本来不在也是成功
export async function deleteHistoryEntry(articleId: string): Promise<{ ok: true } | CommonFailure> {
  if (typeof articleId !== "string" || !isEntityId(articleId)) {
    return UNAVAILABLE;
  }
  const path = `${HISTORY}/${articleId}`;
  const result = await settle(await callAnalytics(path, { method: "DELETE" }), path);
  return "body" in result ? { ok: true } : result;
}

/// 清空全部阅读历史：只动历史，浏览数不受影响
export async function clearHistory(): Promise<{ ok: true } | CommonFailure> {
  const result = await settle(await callAnalytics(HISTORY, { method: "DELETE" }), HISTORY);
  return "body" in result ? { ok: true } : result;
}

/// 改阅读记录开关：关掉只停后续，已有的保留（§4.3）。回保存后的值
export async function setReadingRecording(
  enabled: boolean,
): Promise<{ ok: true; enabled: boolean } | CommonFailure> {
  if (typeof enabled !== "boolean") {
    return UNAVAILABLE;
  }
  const path = `${HISTORY}/recording`;
  const result = await settle(
    await callAnalytics(path, { method: "PUT", json: { enabled } }),
    path,
  );
  if (!("body" in result)) {
    return result;
  }
  const parsed = recordingSchema.safeParse(result.body);
  if (!parsed.success) {
    logUnexpectedResponse(`/patra-analytics${path}`, 200, result.body);
    return UNAVAILABLE;
  }
  return { ok: true, enabled: parsed.data.enabled };
}
