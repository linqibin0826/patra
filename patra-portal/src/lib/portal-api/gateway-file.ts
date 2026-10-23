import "server-only";
import type { ClientOrigin } from "@/lib/client-origin";
import { gatewayStream } from "@/lib/gateway";

interface ProxyGatewayFileOptions {
  /// 透传给浏览器的响应头，其余一概不带
  passthrough: readonly string[];
  /// 要转发的浏览器来源（头像读取带，全文文件不带）
  client?: ClientOrigin;
}

/// 只留白名单里的响应头
function pickHeaders(upstream: Response, names: readonly string[]): Headers {
  const headers = new Headers();
  for (const name of names) {
    const value = upstream.headers.get(name);
    if (value !== null) {
      headers.set(name, value);
    }
  }
  return headers;
}

/// 把网关上的文件（全文 PDF、正文图片、头像）流式转发给浏览器：浏览器带的 `If-None-Match` 原样带上，
/// 200 / 304 透传白名单里的头；上游说不能给（404 及其他 4xx）→ 404；网关出错或连不上 → 502。
/// 浏览器断开时 `request.signal` 中止，向网关的请求随之取消。
export async function proxyGatewayFile(
  path: string,
  request: Request,
  options: ProxyGatewayFileOptions,
): Promise<Response> {
  let upstream: Response;
  try {
    upstream = await gatewayStream(path, {
      ifNoneMatch: request.headers.get("If-None-Match"),
      signal: request.signal,
      ...(options.client ? { client: options.client } : {}),
    });
  } catch {
    return new Response(null, { status: 502 });
  }
  if (upstream.status === 200 || upstream.status === 304) {
    return new Response(upstream.status === 200 ? upstream.body : null, {
      status: upstream.status,
      headers: pickHeaders(upstream, options.passthrough),
    });
  }
  await upstream.body?.cancel();
  return new Response(null, { status: upstream.status < 500 ? 404 : 502 });
}
