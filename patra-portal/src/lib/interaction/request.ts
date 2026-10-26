import "server-only";
import { unstable_rethrow } from "next/navigation";
import { logRequestError, readJson } from "@/lib/auth/gateway-response";
import { readSessionToken } from "@/lib/auth/session-cookie";
import { readClientOrigin } from "@/lib/client-origin";
import { canSendToken, type GatewayRequestInit, gatewayFetch } from "@/lib/gateway";
import { parseProblem } from "@/lib/identity/problem";
import type { IdentityReply } from "@/lib/identity/request";

/// 请求 interaction 的结果，与请求 identity 的同形：`ok`（2xx / 3xx）、`problem`、`no-session`、`error`
export type InteractionReply = IdentityReply;

/// 代表当前浏览器请求 interaction：`path` 是服务内路径（如 `/me/library`）。`auth: "required"`（默认，`/me/**`）
/// 没有可用的会话令牌时不请求、回 `no-session`；`auth: "optional"`（公开收藏夹、主页的收藏夹栏）有令牌就带，
/// 没有照常请求——这些内容对谁可见取决于访客（决策 H）。不写 Cookie：RSC 里不能写，会话失效时由调用方处理。
export async function callInteraction(
  path: string,
  init: Omit<GatewayRequestInit, "token" | "client"> = {},
  options: { auth?: "required" | "optional" } = {},
): Promise<InteractionReply> {
  const endpoint = `/patra-interaction${path}`;
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
