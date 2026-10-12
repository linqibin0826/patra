import "server-only";
import { z } from "zod";
import { logRequestError, logUnexpectedResponse, readJson } from "@/lib/auth/gateway-response";
import { readSessionToken } from "@/lib/auth/session-cookie";
import type { CurrentUser } from "@/lib/auth/types";
import { canSendToken, gatewayFetch } from "@/lib/gateway";

const ME_ENDPOINT = "/patra-identity/auth/me";

/// 读当前用户的结果。
export type SessionLookup =
  | { status: "authenticated"; user: CurrentUser }
  | { status: "anonymous" }
  | { status: "rejected" }
  | { status: "unavailable" };

/// `/auth/me` 的 200 响应里门户要用的字段，`accountType` 不读
const currentUserSchema = z.object({ userId: z.string().min(1), email: z.string().min(1) });

/// 带着会话令牌问网关当前用户是谁。不读也不写 Cookie，不抛异常。
/// - 没有令牌：`anonymous`，不请求网关
/// - 令牌放不进请求头（Cookie 被改坏了）：`rejected`，不请求网关
/// - 200 且字段齐全：`authenticated`
/// - 401（`GW-0401` 或 `IDN-0401`）：`rejected`
/// - 其他状态码、请求抛错、响应体格式不对：`unavailable`
///
/// `timeoutMs` 不传时用 `gatewayFetch` 默认的 10 秒。proxy 传 3 秒。
export async function lookupSessionByToken(
  token: string | null,
  timeoutMs?: number,
): Promise<SessionLookup> {
  if (!token) {
    return { status: "anonymous" };
  }
  if (!canSendToken(token)) {
    return { status: "rejected" };
  }
  try {
    const res = await gatewayFetch(ME_ENDPOINT, { token, timeoutMs });
    if (res.status === 401) {
      return { status: "rejected" };
    }
    const body = await readJson(res);
    const parsed = res.status === 200 ? currentUserSchema.safeParse(body) : undefined;
    if (!parsed?.success) {
      logUnexpectedResponse(ME_ENDPOINT, res.status, body);
      return { status: "unavailable" };
    }
    return { status: "authenticated", user: parsed.data };
  } catch (error) {
    logRequestError(ME_ENDPOINT, error);
    return { status: "unavailable" };
  }
}

/// 读出 Cookie 里的令牌，再按 `lookupSessionByToken` 的规则判断。读 Cookie 抛错时是 `unavailable`。
export async function lookupSession(): Promise<SessionLookup> {
  let token: string | null;
  try {
    token = await readSessionToken();
  } catch (error) {
    logRequestError(ME_ENDPOINT, error);
    return { status: "unavailable" };
  }
  return lookupSessionByToken(token);
}
