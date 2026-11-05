import "server-only";
import { headers } from "next/headers";

/// 回调地址注册在这个域名上（决策 G）
const DEFAULT_OAUTH_HOST = "patra.linqibin.dev";

/// 这次请求的域名能不能走第三方登录。回调地址要提前注册，Cookie 又绑定域名，只有注册过的那个域名走得通：
/// tailnet IP、主机名都不行。本机开发用 `PATRA_OAUTH_HOST=localhost:4000` 覆盖。取域名的规则和 `isSameOrigin` 一致。
export function isOAuthHost(requestHeaders: Headers): boolean {
  const host = requestHeaders.get("x-forwarded-host") ?? requestHeaders.get("host");
  const allowed = process.env.PATRA_OAUTH_HOST?.trim() || DEFAULT_OAUTH_HOST;
  return host?.toLowerCase() === allowed.toLowerCase();
}

/// 当前请求（RSC、Route Handler、Server Action）的 `isOAuthHost`
export async function readOAuthEnabled(): Promise<boolean> {
  return isOAuthHost(await headers());
}
