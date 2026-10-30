import "server-only";
import { unstable_rethrow } from "next/navigation";
import { logRequestError, readJson } from "@/lib/auth/gateway-response";
import { readSessionToken } from "@/lib/auth/session-cookie";
import { readClientOrigin } from "@/lib/client-origin";
import { canSendToken, type GatewayRequestInit, gatewayFetch } from "@/lib/gateway";
import { parseProblem } from "@/lib/identity/problem";
import type { IdentityReply } from "@/lib/identity/request";

/// 请求通知服务的结果，与请求 identity 的同形：`ok`（2xx / 3xx）、`problem`、`no-session`、`error`
export type NotificationReply = IdentityReply;

/// 只留错误类型的同名错误：报错信息可能带着请求地址
function redacted(error: unknown): unknown {
  if (!(error instanceof Error)) {
    return error;
  }
  const copy = new Error("报错信息可能含查询串，不记");
  copy.name = error.name;
  return copy;
}

/// 代表当前浏览器请求通知服务：`path` 是服务内路径（如 `/me/notification-preferences`）。`auth: "required"`
/// （默认，偏好接口）没有可用的会话令牌时不请求、回 `no-session`；`auth: "none"`（三个退订接口）不读 Cookie、不带
/// 令牌：退订只认链接里的令牌，带了会话网关还要去 identity 查一次，identity 挂了就退订不了（PAP-142 第 8.6 节）。
/// `query` 拼进请求地址，日志只记不带查询串的路径：退订令牌在查询串里，不能进门户日志。
export async function callNotification(
  path: string,
  init: Omit<GatewayRequestInit, "token" | "client"> = {},
  options: { auth?: "required" | "none"; query?: URLSearchParams } = {},
): Promise<NotificationReply> {
  const endpoint = `/patra-notification${path}`;
  const search = options.query?.toString();
  try {
    const stored = options.auth === "none" ? null : await readSessionToken();
    const token = stored && canSendToken(stored) ? stored : null;
    if (!token && options.auth !== "none") {
      return { kind: "no-session" };
    }
    const res = await gatewayFetch(search ? `${endpoint}?${search}` : endpoint, {
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
    // 带查询串时只记错误类型：网关地址配错（比如漏了 `http://`）时 fetch 的报错信息里是完整地址，令牌就在里面
    logRequestError(endpoint, search ? redacted(error) : error);
    return { kind: "error" };
  }
}
