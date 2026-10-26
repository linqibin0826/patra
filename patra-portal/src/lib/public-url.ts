import "server-only";
import { headers } from "next/headers";

/// 门户的公开绝对地址（「复制链接」与 canonical 共用）。取配置 `PATRA_PORTAL_PUBLIC_URL`：不能用浏览器的
/// `location.origin`，否则本机、tailscale IP、主机名访问会复制出不同的链接（简报 §3.18）。没配时用这次
/// 请求的 `Host` 拼 `http://` 地址，开发机不配也能用。
export async function publicUrl(path: string): Promise<string> {
  const configured = process.env.PATRA_PORTAL_PUBLIC_URL?.trim();
  if (configured) {
    return `${configured.replace(/\/+$/, "")}${path}`;
  }
  const host = (await headers()).get("host") ?? "localhost:4000";
  return `http://${host}${path}`;
}
