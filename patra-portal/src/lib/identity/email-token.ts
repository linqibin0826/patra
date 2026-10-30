/// 邮件链接里的令牌：32 字节随机数的 base64url，无填充，恰好 43 个字符（release spec 2.1.7）。v0.16 的退订令牌
/// 也是这个形状（PAP-142 第 6.1 节）
export const TOKEN_PATTERN = /^[A-Za-z0-9_-]{43}$/;

/// 从地址的片段（`location.hash`）里读 `token` 参数，格式不对是 null。片段不随 HTTP 请求发出，邮件网关预取、
/// 门户日志、Referer 都拿不到它。
export function readFragmentToken(hash: string): string | null {
  const token = new URLSearchParams(hash.replace(/^#/, "")).get("token");
  return token !== null && TOKEN_PATTERN.test(token) ? token : null;
}
