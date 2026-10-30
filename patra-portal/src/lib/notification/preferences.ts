import "server-only";
import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import { callNotification } from "@/lib/notification/request";
import { type NotificationPreferences, preferencesSchema } from "@/lib/notification/schema";

const PATH = "/me/notification-preferences";
const ENDPOINT = `/patra-notification${PATH}`;

/// RSC 里读本人的提醒邮件偏好（`GET /me/notification-preferences`）。没有会话或 401 是 `signed-out`（页面跳登录页，
/// RSC 里不能清 Cookie）；其余失败记日志后是 `unavailable`。不抛错：通知服务不可用时设置页要在原地出「暂时打不开」
/// 的卡片，其余分区照常（简报 §3.5）。
export async function fetchMyNotificationPreferences(): Promise<
  NotificationPreferences | "signed-out" | "unavailable"
> {
  const reply = await callNotification(PATH);
  if (reply.kind === "no-session") {
    return "signed-out";
  }
  if (reply.kind === "error") {
    return "unavailable";
  }
  if (reply.kind === "problem") {
    if (reply.problem.status === 401) {
      return "signed-out";
    }
    logUnexpectedResponse(ENDPOINT, reply.problem.status, { code: reply.problem.code });
    return "unavailable";
  }
  const parsed = reply.res.status === 200 ? preferencesSchema.safeParse(reply.body) : undefined;
  if (!parsed?.success) {
    logUnexpectedResponse(ENDPOINT, reply.res.status, reply.body);
    return "unavailable";
  }
  return parsed.data;
}
