import "server-only";

/// 默认超时：慢后端不应无限阻塞服务端渲染
const DEFAULT_TIMEOUT_MS = 10_000;

/// HTTP 头的值里允许出现的字符（RFC 9110 的 field-value）：制表符、空格、可见 ASCII、0x80–0xFF
const HEADER_VALUE_CHARS = /^[\t\x20-\x7e\x80-\xff]*$/;

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

/// 令牌能不能放进 `Authorization` 头。Cookie 被改坏时，读出来的值可能含有换行、控制字符或
/// 0xFF 以上的字符：带着它请求，fetch 在发出之前就会抛错，错误消息里还可能带着这个值。
/// 这样的值不可能是有效令牌，调用方不要拿它请求网关。
export function canSendToken(token: string): boolean {
  return HEADER_VALUE_CHARS.test(token);
}
