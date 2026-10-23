import "server-only";
import { unstable_rethrow } from "next/navigation";
import { logRequestError, readJson } from "@/lib/auth/gateway-response";
import { readSessionToken } from "@/lib/auth/session-cookie";
import { readClientOrigin } from "@/lib/client-origin";
import { canSendToken, type GatewayRequestInit, gatewayFetch } from "@/lib/gateway";
import { type Problem, parseProblem } from "@/lib/identity/problem";

/// 请求 identity 的结果：
/// - `ok`：2xx 或 3xx，带读出的响应体
/// - `problem`：其余状态码，带解析出的 ProblemDetail
/// - `no-session`：要求登录，但没有会话令牌或令牌放不进请求头，没有请求网关
/// - `error`：请求抛错（超时、网络、缺配置），已记日志
export type IdentityReply =
  | { kind: "ok"; res: Response; body: unknown }
  | { kind: "problem"; problem: Problem }
  | { kind: "no-session" }
  | { kind: "error" };

/// 代表当前浏览器请求 identity：`path` 是服务内路径（如 `/me/profile`），带上会话令牌与浏览器来源。
/// `auth: "optional"`（公开主页）时没有令牌也照常请求。不读写 Cookie 以外的状态，也不清 Cookie：
/// RSC 里不能写 Cookie，会话失效时怎么办由调用方决定。
export async function callIdentity(
  path: string,
  init: Omit<GatewayRequestInit, "token" | "client"> = {},
  options: { auth?: "required" | "optional" } = {},
): Promise<IdentityReply> {
  const endpoint = `/patra-identity${path}`;
  try {
    const stored = await readSessionToken();
    const token = stored && canSendToken(stored) ? stored : null;
    if (!token && (options.auth ?? "required") === "required") {
      return { kind: "no-session" };
    }
    const res = await gatewayFetch(endpoint, {
      ...init,
      ...(token ? { token } : {}),
      client: await readClientOrigin(),
    });
    const body = await readJson(res);
    return res.status < 400
      ? { kind: "ok", res, body }
      : { kind: "problem", problem: parseProblem(res, body) };
  } catch (error) {
    // 静态渲染时 Next 用抛错切到动态渲染（读 Cookie、请求头），这个信号必须原样抛出去
    unstable_rethrow(error);
    logRequestError(endpoint, error);
    return { kind: "error" };
  }
}
