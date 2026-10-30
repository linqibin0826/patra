"use server";

import { z } from "zod";
import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import { clearSessionCookie } from "@/lib/auth/session-cookie";
import { isSessionRejected } from "@/lib/identity/problem";
import { callNotification } from "@/lib/notification/request";
import {
  type NotificationMode,
  type NotificationPreferences,
  notificationModeSchema,
  PREFERENCE_CATEGORIES,
  type PreferenceCategory,
  preferencesSchema,
} from "@/lib/notification/schema";

/// 保存一类档位的结果
export type SaveModeResult =
  | { ok: true; preferences: NotificationPreferences }
  | { ok: false; reason: "signed-out" | "unavailable" };

const UNAVAILABLE = { ok: false, reason: "unavailable" } as const;

const inputSchema = z.object({
  category: z.enum(PREFERENCE_CATEGORIES),
  mode: notificationModeSchema,
});

/// 保存一类的档位（`PUT /me/notification-preferences/{category}`），返回两类当前的档位。会话失效时清掉会话 Cookie；
/// 422 是门户自己发错了值，按 `unavailable` 处理并记日志。
export async function saveNotificationMode(
  category: PreferenceCategory,
  mode: NotificationMode,
): Promise<SaveModeResult> {
  const input = inputSchema.safeParse({ category, mode });
  if (!input.success) {
    return UNAVAILABLE;
  }
  const path = `/me/notification-preferences/${input.data.category}`;
  const endpoint = `/patra-notification${path}`;
  const reply = await callNotification(path, { method: "PUT", json: { mode: input.data.mode } });
  if (reply.kind === "no-session") {
    return { ok: false, reason: "signed-out" };
  }
  if (reply.kind === "error") {
    return UNAVAILABLE;
  }
  if (reply.kind === "problem") {
    if (isSessionRejected(reply.problem)) {
      await clearSessionCookie();
      return { ok: false, reason: "signed-out" };
    }
    logUnexpectedResponse(endpoint, reply.problem.status, { code: reply.problem.code });
    return UNAVAILABLE;
  }
  const parsed = reply.res.status === 200 ? preferencesSchema.safeParse(reply.body) : undefined;
  if (!parsed?.success) {
    logUnexpectedResponse(endpoint, reply.res.status, reply.body);
    return UNAVAILABLE;
  }
  return { ok: true, preferences: parsed.data };
}
