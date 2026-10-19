import "server-only";
import { gatewayStream } from "@/lib/gateway";

/// 透传给浏览器的响应头：类型、长度、缓存与下载文件名由 catalog 设置（网关不补），其余一概不带。
const PASSTHROUGH_HEADERS = [
  "Content-Type",
  "Content-Length",
  "ETag",
  "Cache-Control",
  "Content-Disposition",
  "Last-Modified",
];

/// 详情 id 与正文图片文件名的形状（catalog 同样校验，这里先挡掉，坏请求不打网关）。
const PUBLICATION_ID = /^[1-9]\d*$/;
const FIGURE_FILE = /^[A-Za-z0-9._-]+$/;

export function isPublicationId(id: string): boolean {
  return PUBLICATION_ID.test(id);
}

export function isFigureFileName(file: string): boolean {
  return FIGURE_FILE.test(file) && file !== "." && file !== "..";
}

function pickHeaders(upstream: Response): Headers {
  const headers = new Headers();
  for (const name of PASSTHROUGH_HEADERS) {
    const value = upstream.headers.get(name);
    if (value !== null) {
      headers.set(name, value);
    }
  }
  return headers;
}

/**
 * 把 catalog 的全文文件（PDF、正文图片）经网关流式转发给浏览器（release spec 决策 F）。
 * 200 / 304 透传；catalog 说不能给（404 及其他 4xx）→ 404；网关出错或连不上 → 502。
 * 浏览器断开下载时 `request.signal` 中止，向网关的请求随之取消。
 */
export async function proxyFullTextFile(path: string, request: Request): Promise<Response> {
  let upstream: Response;
  try {
    upstream = await gatewayStream(path, {
      ifNoneMatch: request.headers.get("If-None-Match"),
      signal: request.signal,
    });
  } catch {
    return new Response(null, { status: 502 });
  }
  if (upstream.status === 200 || upstream.status === 304) {
    return new Response(upstream.status === 200 ? upstream.body : null, {
      status: upstream.status,
      headers: pickHeaders(upstream),
    });
  }
  await upstream.body?.cancel();
  return new Response(null, { status: upstream.status < 500 ? 404 : 502 });
}
