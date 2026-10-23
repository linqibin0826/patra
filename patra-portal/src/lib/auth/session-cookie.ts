import "server-only";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

/// 有效期 30 天，和会话的不活跃过期时间一致
const MAX_AGE_SECONDS = 30 * 24 * 60 * 60;

/// `Secure` 开关 `PATRA_SESSION_COOKIE_SECURE`：不设或为 `true` 时开，为 `false` 时关，其他值报错。
/// 每次读写 Cookie 时读取。
function secureEnabled(): boolean {
  const value = process.env.PATRA_SESSION_COOKIE_SECURE;
  if (value === undefined || value === "true") {
    return true;
  }
  if (value === "false") {
    return false;
  }
  throw new Error("PATRA_SESSION_COOKIE_SECURE 只能是 true 或 false");
}

/// 会话 Cookie 的名称和固定属性。`Secure` 开时名字带 `__Host-` 前缀：浏览器只接受本站经 HTTPS 写入、
/// `Path=/`、不带 `Domain` 的那一份，同一域名下的其他子站点没法种入或覆盖它。这个前缀要求必须带
/// `Secure`，所以 HTTP 环境只能用不带前缀的名字。
function sessionCookie() {
  const secure = secureEnabled();
  return {
    name: secure ? "__Host-patra_session" : "patra_session",
    httpOnly: true,
    sameSite: "lax",
    path: "/",
    secure,
  } as const;
}

/// 读会话令牌；没有 Cookie 或值为空时返回 null。
export async function readSessionToken(): Promise<string | null> {
  const value = (await cookies()).get(sessionCookie().name)?.value;
  return value ? value : null;
}

/// proxy 里用：从请求对象读会话令牌，规则和 `readSessionToken` 相同。
export function readRequestSessionToken(req: NextRequest): string | null {
  const value = req.cookies.get(sessionCookie().name)?.value;
  return value ? value : null;
}

/// 注册、登录成功时写入令牌，有效期 30 天。
export async function writeSessionCookie(token: string): Promise<void> {
  (await cookies()).set({ ...sessionCookie(), value: token, maxAge: MAX_AGE_SECONDS });
}

/// 用原值重写一次，把有效期顺延回 30 天；没有 Cookie 时什么也不做。
export async function refreshSessionCookie(): Promise<void> {
  const token = await readSessionToken();
  if (token) {
    await writeSessionCookie(token);
  }
}

/// 清除：写入空值，属性和写入时相同，`Max-Age=0`。不用 `cookies().delete()`：它不带 `Secure`，
/// 浏览器会拒绝用它删除 `__Host-` 开头的 Cookie。
export async function clearSessionCookie(): Promise<void> {
  (await cookies()).set({ ...sessionCookie(), value: "", maxAge: 0 });
}

/// 设备标识的格式：32 字节随机数的 base64url，无填充，恰好 43 个字符（identity `DeviceId` 同一条规则）
export const DEVICE_ID_PATTERN = /^[A-Za-z0-9_-]{43}$/;

/// 400 天：Chrome 对 Cookie 有效期的上限
const DEVICE_MAX_AGE_SECONDS = 400 * 24 * 60 * 60;

/// 设备标识 Cookie 的名称和固定属性，`Secure` 与会话 Cookie 同一个开关
function deviceCookie() {
  const secure = secureEnabled();
  return {
    name: secure ? "__Host-patra_device" : "patra_device",
    httpOnly: true,
    sameSite: "lax",
    path: "/",
    secure,
  } as const;
}

/// 登录、注册前取设备标识（登录设备 spec 第 7 节）：Cookie 里的值格式对就原样用，没有或被改坏了就
/// 新生成一个；两种情况都写回一次，有效期顺延到 400 天。登出、会话失效都不清它。
export async function ensureDeviceId(): Promise<string> {
  const store = await cookies();
  const cookie = deviceCookie();
  const stored = store.get(cookie.name)?.value;
  const id =
    stored && DEVICE_ID_PATTERN.test(stored)
      ? stored
      : Buffer.from(crypto.getRandomValues(new Uint8Array(32))).toString("base64url");
  store.set({ ...cookie, value: id, maxAge: DEVICE_MAX_AGE_SECONDS });
  return id;
}
