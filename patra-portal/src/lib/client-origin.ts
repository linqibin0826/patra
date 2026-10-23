import "server-only";
import { isIP } from "node:net";
import { headers } from "next/headers";
import { isSendableHeaderValue } from "@/lib/gateway";

/// 门户代表浏览器请求网关时要转发的浏览器来源：`ip` 写成 `X-Forwarded-For`，`userAgent` 写成
/// `User-Agent`。取不到或不合格时为 null，不发对应的头。
export interface ClientOrigin {
  ip: string | null;
  userAgent: string | null;
}

/// IPv4 映射的 IPv6 地址（`::ffff:10.0.0.2`）里的 IPv4 部分
const IPV4_MAPPED = /^::ffff:(\d{1,3}(?:\.\d{1,3}){3})$/i;

/// `x-forwarded-for` 的值 → 单个 IP 字面量。生产入口已删掉浏览器带来的这个头，Next 填进去的是连接
/// 地址（工程 spec §6.1）。多个值（含逗号）说明入口没装预加载模块，宁可不发也不发浏览器给的值；
/// IPv4 映射地址还原成 IPv4，IPv6 的 zone（`%eth0`）去掉；不是 IP 字面量时为 null。
function clientIp(raw: string | null): string | null {
  if (raw === null || raw.includes(",")) {
    return null;
  }
  const trimmed = raw.trim();
  const unzoned = trimmed.includes(":") ? trimmed.replace(/%.*$/, "") : trimmed;
  const ip = IPV4_MAPPED.exec(unzoned)?.[1] ?? unzoned;
  return isIP(ip) === 0 ? null : ip;
}

/// 从请求头取浏览器来源。proxy 用 `req.headers` 调它，其余地方用 `readClientOrigin`。
export function clientOriginFromHeaders(headers: Headers): ClientOrigin {
  const userAgent = headers.get("user-agent");
  return {
    ip: clientIp(headers.get("x-forwarded-for")),
    userAgent: userAgent && isSendableHeaderValue(userAgent) ? userAgent : null,
  };
}

/// 读当前请求（Server Action、Route Handler、RSC）的浏览器来源。
export async function readClientOrigin(): Promise<ClientOrigin> {
  return clientOriginFromHeaders(await headers());
}
