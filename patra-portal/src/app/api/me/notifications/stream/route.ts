import { logRequestError } from "@/lib/auth/gateway-response";
import { readSessionToken } from "@/lib/auth/session-cookie";
import { readClientOrigin } from "@/lib/client-origin";
import { canSendToken, gatewayStream } from "@/lib/gateway";

export const dynamic = "force-dynamic";

const ENDPOINT = "/patra-notification/me/notifications/stream";

/// 失败时的响应：只有状态码（`EventSource` 看不到响应体，收到非 200 就不再自动重连）
const status = (code: number) =>
  new Response(null, { status: code, headers: { "Cache-Control": "private, no-store" } });

/// 未读数的实时推送（PAP-149 spec 第 4.2 节）：浏览器的 `EventSource` 带不了请求头，所以连这里，由这里从会话 Cookie
/// 读令牌、带上 Bearer 连网关的事件流，响应体原样透传。不缓冲、不压缩（`no-transform`），浏览器断开时一并中止上游。
/// 上游非 200 原样回状态码；网关连不上 502。
export async function GET(request: Request): Promise<Response> {
  const stored = await readSessionToken();
  if (!stored || !canSendToken(stored)) {
    return status(401);
  }
  let upstream: Response;
  try {
    upstream = await gatewayStream(ENDPOINT, {
      token: stored,
      accept: "text/event-stream",
      signal: request.signal,
      client: await readClientOrigin(),
    });
  } catch (error) {
    // 浏览器先断开了（关了页面、切到后台）：不是错误
    if (
      !request.signal.aborted &&
      !(error instanceof DOMException && error.name === "AbortError")
    ) {
      logRequestError(ENDPOINT, error);
    }
    return status(502);
  }
  if (upstream.status !== 200 || !upstream.body) {
    void upstream.body?.cancel();
    return status(upstream.status === 200 ? 502 : upstream.status);
  }
  return new Response(upstream.body, {
    status: 200,
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache, no-transform",
      "X-Accel-Buffering": "no",
    },
  });
}
