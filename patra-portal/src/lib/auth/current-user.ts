import "server-only";
import { unstable_rethrow } from "next/navigation";
import { cache } from "react";
import { logRequestError, logUnexpectedResponse, readJson } from "@/lib/auth/gateway-response";
import { readSessionToken } from "@/lib/auth/session-cookie";
import type { CurrentUser } from "@/lib/auth/types";
import { type ClientOrigin, readClientOrigin } from "@/lib/client-origin";
import { canSendToken, gatewayFetch } from "@/lib/gateway";
import { userSchema } from "@/lib/identity/user-schema";

const ME_ENDPOINT = "/patra-identity/auth/me";

/// 读当前用户的结果。
export type SessionLookup =
  | { status: "authenticated"; user: CurrentUser }
  | { status: "anonymous" }
  | { status: "rejected" }
  | { status: "unavailable" };

/// 带着会话令牌问网关当前用户是谁。不读也不写 Cookie，不抛异常。
/// - 没有令牌：`anonymous`，不请求网关
/// - 令牌放不进请求头（Cookie 被改坏了）：`rejected`，不请求网关
/// - 200 且字段齐全：`authenticated`
/// - 401（`GW-0401` 或 `IDN-0401`）：`rejected`
/// - 其他状态码、请求抛错、响应体格式不对：`unavailable`
///
/// `timeoutMs` 不传时用 `gatewayFetch` 默认的 10 秒，proxy 传 3 秒；`client` 是要转发的浏览器来源。
export async function lookupSessionByToken(
  token: string | null,
  options: { timeoutMs?: number; client?: ClientOrigin } = {},
): Promise<SessionLookup> {
  if (!token) {
    return { status: "anonymous" };
  }
  if (!canSendToken(token)) {
    return { status: "rejected" };
  }
  try {
    const res = await gatewayFetch(ME_ENDPOINT, { token, ...options });
    if (res.status === 401) {
      return { status: "rejected" };
    }
    const body = await readJson(res);
    const parsed = res.status === 200 ? userSchema.safeParse(body) : undefined;
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

/// 读出 Cookie 里的令牌，再按 `lookupSessionByToken` 的规则判断，转发当前请求的浏览器来源。读 Cookie
/// 抛错时是 `unavailable`。用 React `cache()` 包住：同一次服务端渲染里（设置页的 layout 与 page、主页的
/// `generateMetadata` 与 page）只请求一次网关。
export const lookupSession = cache(async (): Promise<SessionLookup> => {
  let token: string | null;
  try {
    token = await readSessionToken();
  } catch (error) {
    // 静态渲染时 Next 用抛错切到动态渲染，这个信号必须原样抛出去
    unstable_rethrow(error);
    logRequestError(ME_ENDPOINT, error);
    return { status: "unavailable" };
  }
  return lookupSessionByToken(token, { client: await readClientOrigin() });
});
