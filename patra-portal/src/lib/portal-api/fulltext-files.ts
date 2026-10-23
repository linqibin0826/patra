import "server-only";
import { proxyGatewayFile } from "@/lib/portal-api/gateway-file";

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

/**
 * 把 catalog 的全文文件（PDF、正文图片）经网关流式转发给浏览器（release spec 决策 F）。
 * 200 / 304 透传；catalog 说不能给（404 及其他 4xx）→ 404；网关出错或连不上 → 502。
 */
export async function proxyFullTextFile(path: string, request: Request): Promise<Response> {
  return proxyGatewayFile(path, request, { passthrough: PASSTHROUGH_HEADERS });
}
