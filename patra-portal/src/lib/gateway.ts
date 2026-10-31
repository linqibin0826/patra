import "server-only";
import type { ClientOrigin } from "@/lib/client-origin";

/// 默认超时：慢后端不应无限阻塞服务端渲染
const DEFAULT_TIMEOUT_MS = 10_000;

/// HTTP 头的值里允许出现的字符（RFC 9110 的 field-value）：制表符、空格、可见 ASCII、0x80–0xFF
const HEADER_VALUE_CHARS = /^[\t\x20-\x7e\x80-\xff]*$/;

export interface GatewayRequestInit {
  /// 默认 GET
  method?: "GET" | "HEAD" | "POST" | "PUT" | "PATCH" | "DELETE";
  /// 有值时序列化成 JSON 请求体，并带 `Content-Type: application/json`；与 `body` 互斥
  json?: unknown;
  /// multipart 请求体，`Content-Type`（带边界）由 fetch 生成；与 `json` 互斥
  body?: FormData;
  /// 会话令牌，有值时写成 `Authorization: Bearer <token>`
  token?: string;
  /// 浏览器来源：门户代表浏览器请求 identity 时传，写成 `X-Forwarded-For` 与 `User-Agent`
  client?: ClientOrigin;
  /// 默认 `manual`：网关不跟随下游的 3xx，原样交给门户（旧用户名的 301 要读响应体），跟随只会去请求
  /// 缺了服务前缀的地址
  redirect?: "follow" | "manual";
  /// 超时毫秒数，默认 10_000
  timeoutMs?: number;
  /// 浏览器带来的 `If-None-Match`，原样转给网关（未读数没变时网关回 304）
  ifNoneMatch?: string | null;
}

/// 统一网关客户端（server-only）：全仓库只有这里读 `PATRA_GATEWAY_BASE_URL`、向网关发请求。
/// 只负责把请求发出去：原样返回网关的响应，不解读状态码；网络错误和超时照常抛出。
/// 没传令牌就不带 `Authorization`，所以正文请求不会带上用户的令牌。
export async function gatewayFetch(path: string, init: GatewayRequestInit = {}): Promise<Response> {
  const baseUrl = process.env.PATRA_GATEWAY_BASE_URL;
  if (!baseUrl) {
    throw new Error("PATRA_GATEWAY_BASE_URL 未配置");
  }
  if (init.json !== undefined && init.body !== undefined) {
    throw new Error("json 和 body 只能给一个");
  }
  const headers: Record<string, string> = { ...forwardingHeaders(init.client) };
  if (init.token) {
    headers.Authorization = `Bearer ${init.token}`;
  }
  if (init.ifNoneMatch) {
    headers["If-None-Match"] = init.ifNoneMatch;
  }
  if (init.json !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  const body = init.json === undefined ? init.body : JSON.stringify(init.json);
  return fetch(`${baseUrl}${path}`, {
    method: init.method ?? "GET",
    headers,
    cache: "no-store",
    redirect: init.redirect ?? "manual",
    signal: AbortSignal.timeout(init.timeoutMs ?? DEFAULT_TIMEOUT_MS),
    ...(body === undefined ? {} : { body }),
  });
}

/// 浏览器来源 → 转发给网关的两个头。只写这两个：网关对受信连接会按 `X-Forwarded-Host` / `-Proto` /
/// `-Port` 改写主机与协议，门户从不发它们（工程 spec §6.2）。值为 null 的不写。
function forwardingHeaders(client: ClientOrigin | undefined): Record<string, string> {
  const headers: Record<string, string> = {};
  if (client?.ip) {
    headers["X-Forwarded-For"] = client.ip;
  }
  if (client?.userAgent) {
    headers["User-Agent"] = client.userAgent;
  }
  return headers;
}

export interface GatewayStreamInit {
  /// 默认 GET；导出这类要带请求体的下载用 POST
  method?: "GET" | "POST";
  /// 有值时序列化成 JSON 请求体，并带 `Content-Type: application/json`
  json?: unknown;
  /// 浏览器带来的 `If-None-Match`，原样转给网关（命中时网关回 304）
  ifNoneMatch?: string | null;
  /// 调用方的中止信号：浏览器断开下载时，一并取消向网关的请求
  signal?: AbortSignal;
  /// 等响应头的超时毫秒数，默认 10_000
  headerTimeoutMs?: number;
  /// 浏览器来源，同 `GatewayRequestInit.client`
  client?: ClientOrigin;
  /// 会话令牌，有值时写成 `Authorization: Bearer <token>`（实时推送的事件流要登录）
  token?: string;
  /// `Accept` 头，如推送的 `text/event-stream`
  accept?: string;
}

/// 取网关上的文件（PDF、正文图片、导出文件）与提醒的实时推送，响应体原样流式交给调用方转发。
/// 与 `gatewayFetch` 的区别在超时：只计到响应头到达为止。之后的下载可能持续几分钟（155 MB 的 PDF、
/// 慢速网络），不能用总时长掐断；下载中途卡住由 fetch 自带的两次读之间的空闲超时兜底。
export async function gatewayStream(path: string, init: GatewayStreamInit = {}): Promise<Response> {
  const baseUrl = process.env.PATRA_GATEWAY_BASE_URL;
  if (!baseUrl) {
    throw new Error("PATRA_GATEWAY_BASE_URL 未配置");
  }
  const headerTimeout = new AbortController();
  const timer = setTimeout(
    () => headerTimeout.abort(new DOMException("网关响应头超时", "TimeoutError")),
    init.headerTimeoutMs ?? DEFAULT_TIMEOUT_MS,
  );
  const signal = init.signal
    ? AbortSignal.any([headerTimeout.signal, init.signal])
    : headerTimeout.signal;
  try {
    return await fetch(`${baseUrl}${path}`, {
      method: init.method ?? "GET",
      headers: {
        ...forwardingHeaders(init.client),
        ...(init.token ? { Authorization: `Bearer ${init.token}` } : {}),
        ...(init.accept ? { Accept: init.accept } : {}),
        ...(init.ifNoneMatch ? { "If-None-Match": init.ifNoneMatch } : {}),
        ...(init.json === undefined ? {} : { "Content-Type": "application/json" }),
      },
      cache: "no-store",
      signal,
      ...(init.json === undefined ? {} : { body: JSON.stringify(init.json) }),
    });
  } finally {
    clearTimeout(timer);
  }
}

/// 一个值能不能放进 HTTP 请求头：含换行、控制字符或 0xFF 以上的字符时，fetch 在发出之前就会抛错，
/// 错误消息里还可能带着这个值。
export function isSendableHeaderValue(value: string): boolean {
  return HEADER_VALUE_CHARS.test(value);
}

/// 令牌能不能放进 `Authorization` 头。Cookie 被改坏时，读出来的值可能放不进请求头（见
/// `isSendableHeaderValue`）：这样的值不可能是有效令牌，调用方不要拿它请求网关。
export function canSendToken(token: string): boolean {
  return isSendableHeaderValue(token);
}
