import "server-only";
import { z } from "zod";
import { logRequestError, logUnexpectedResponse, readJson } from "@/lib/auth/gateway-response";
import { readSessionToken } from "@/lib/auth/session-cookie";
import type { CurrentUser } from "@/lib/auth/types";
import { gatewayFetch } from "@/lib/gateway";

const ME_ENDPOINT = "/patra-identity/auth/me";

/// 读当前用户的结果。
export type SessionLookup =
  | { status: "authenticated"; user: CurrentUser }
  | { status: "anonymous" }
  | { status: "rejected" }
  | { status: "unavailable" };

/// `/auth/me` 的 200 响应里门户要用的字段，`accountType` 不读
const currentUserSchema = z.object({ userId: z.string().min(1), email: z.string().min(1) });

/// 带着会话令牌问网关当前用户是谁。不抛异常，也不写 Cookie。
/// - 没有 Cookie：`anonymous`，不请求网关
/// - 200 且字段齐全：`authenticated`
/// - 401（`GW-0401` 或 `IDN-0401`）：`rejected`
/// - 其他状态码、请求抛错、响应体格式不对、读 Cookie 抛错：`unavailable`
export async function lookupSession(): Promise<SessionLookup> {
  try {
    const token = await readSessionToken();
    if (!token) {
      return { status: "anonymous" };
    }
    const res = await gatewayFetch(ME_ENDPOINT, { token });
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

/// 服务端渲染的页面判断是否已登录时用（比如登录页「已登录就跳走」）。渲染时不能写 Cookie，
/// 所以收到 401 也只返回 null，过期的 Cookie 留给下一次 `/api/auth/me` 清除。
export async function getCurrentUser(): Promise<CurrentUser | null> {
  const session = await lookupSession();
  return session.status === "authenticated" ? session.user : null;
}
