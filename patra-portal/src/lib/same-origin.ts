/// 写操作的 Route Handler 防跨站请求伪造：`Origin` 必须存在，且它的主机（含端口）等于这次请求的主机
/// （先看 `X-Forwarded-Host`，再看 `Host`，与 Server Action 的比对规则一致）。Server Action 自带这道检查，
/// Route Handler 没有；mini 上各服务同一个 IP、只是端口不同，浏览器算同一站点，`SameSite=Lax` 挡不住。
export function isSameOrigin(request: Request): boolean {
  const origin = request.headers.get("origin");
  const host = request.headers.get("x-forwarded-host") ?? request.headers.get("host");
  if (!origin || !host) {
    return false;
  }
  try {
    return new URL(origin).host === host;
  } catch {
    return false;
  }
}
