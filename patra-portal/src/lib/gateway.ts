import "server-only";

/// 默认超时：慢后端不应无限阻塞服务端渲染
const DEFAULT_TIMEOUT_MS = 10_000;

export interface GatewayRequestInit {
  /// 默认 GET
  method?: "GET" | "HEAD" | "POST";
  /// 有值时序列化成 JSON 请求体，并带 `Content-Type: application/json`
  json?: unknown;
  /// 会话令牌，有值时写成 `Authorization: Bearer <token>`
  token?: string;
  /// 超时毫秒数，默认 10_000
  timeoutMs?: number;
}

/// 统一网关客户端（server-only）：全仓库只有这里读 `PATRA_GATEWAY_BASE_URL`、向网关发请求。
/// 只负责把请求发出去：原样返回网关的响应，不解读状态码；网络错误和超时照常抛出。
/// 没传令牌就不带 `Authorization`，所以正文请求不会带上用户的令牌。
export async function gatewayFetch(path: string, init: GatewayRequestInit = {}): Promise<Response> {
  const baseUrl = process.env.PATRA_GATEWAY_BASE_URL;
  if (!baseUrl) {
    throw new Error("PATRA_GATEWAY_BASE_URL 未配置");
  }
  const headers: Record<string, string> = {};
  if (init.token) {
    headers.Authorization = `Bearer ${init.token}`;
  }
  if (init.json !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  return fetch(`${baseUrl}${path}`, {
    method: init.method ?? "GET",
    headers,
    cache: "no-store",
    signal: AbortSignal.timeout(init.timeoutMs ?? DEFAULT_TIMEOUT_MS),
    ...(init.json === undefined ? {} : { body: JSON.stringify(init.json) }),
  });
}
