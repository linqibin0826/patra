"use server";

import type { z } from "zod";
import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import { TOKEN_PATTERN } from "@/lib/identity/email-token";
import { callNotification, type NotificationReply } from "@/lib/notification/request";
import {
  LINK_CATEGORIES,
  type LinkCategory,
  UNSUBSCRIBE_CATEGORIES,
  type UnsubscribeCategory,
  type UnsubscribeLink,
  type UnsubscribeResult,
  unsubscribeLinkSchema,
  unsubscribeResultSchema,
} from "@/lib/notification/schema";

/// 退订落地页的两个接口（PAP-142 第 8.2、8.3 节）：免登录，**不带会话令牌**（退订只认链接里的令牌，带了网关会先去
/// identity 查会话，identity 挂了就退订不了）。日志只记路径：令牌在查询串或请求体里，不能进门户日志。

/// 两个接口共同的失败：链接无效（令牌缺失、格式不对、不存在、账号已注销），或服务不可用
export type UnsubscribeFailure = { ok: false; reason: "invalid" | "unavailable" };

const INVALID = { ok: false, reason: "invalid" } as const;
const UNAVAILABLE = { ok: false, reason: "unavailable" } as const;

const PATH = "/unsubscribe";
const ENDPOINT = `/patra-notification${PATH}`;

/// 一次回复 → 结果：200 且形状对是成功；只有 404 且原因码是 `UNSUBSCRIBE_LINK_INVALID` 才算无效（裸 404 是网关
/// 路由缺失）；其余记日志后是服务不可用
function settle<T>(
  reply: NotificationReply,
  schema: z.ZodType<T>,
): { ok: true; data: T } | UnsubscribeFailure {
  if (reply.kind === "problem") {
    if (reply.problem.status === 404 && reply.problem.traits.has("UNSUBSCRIBE_LINK_INVALID")) {
      return INVALID;
    }
    logUnexpectedResponse(ENDPOINT, reply.problem.status, { code: reply.problem.code });
    return UNAVAILABLE;
  }
  if (reply.kind !== "ok") {
    return UNAVAILABLE;
  }
  const parsed = reply.res.status === 200 ? schema.safeParse(reply.body) : undefined;
  if (!parsed?.success) {
    logUnexpectedResponse(ENDPOINT, reply.res.status, reply.body);
    return UNAVAILABLE;
  }
  return { ok: true, data: parsed.data };
}

/// 令牌与类别的形状：不合格就是无效，不请求网关
const isValidInput = (token: string, category: string, allowed: readonly string[]) =>
  typeof token === "string" && TOKEN_PATTERN.test(token) && allowed.includes(category);

/// 读链接信息（`GET /unsubscribe?token&category`）：只读，不改任何东西
export async function readUnsubscribeLink(
  token: string,
  category: LinkCategory,
): Promise<{ ok: true; link: UnsubscribeLink } | UnsubscribeFailure> {
  if (!isValidInput(token, category, LINK_CATEGORIES)) {
    return INVALID;
  }
  const query = new URLSearchParams({ token, category });
  const result = settle(
    await callNotification(PATH, {}, { auth: "none", query }),
    unsubscribeLinkSchema,
  );
  return result.ok ? { ok: true, link: result.data } : result;
}

/// 退订这一类或关闭全部（`POST /unsubscribe`，`category` 为 `ALL` 时关闭全部）。重复退订照常成功，`turnedOff` 为空
export async function unsubscribe(
  token: string,
  category: UnsubscribeCategory,
): Promise<{ ok: true; result: UnsubscribeResult } | UnsubscribeFailure> {
  if (!isValidInput(token, category, UNSUBSCRIBE_CATEGORIES)) {
    return INVALID;
  }
  const result = settle(
    await callNotification(PATH, { method: "POST", json: { token, category } }, { auth: "none" }),
    unsubscribeResultSchema,
  );
  return result.ok ? { ok: true, result: result.data } : result;
}
