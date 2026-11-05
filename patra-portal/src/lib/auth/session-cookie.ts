import "server-only";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";
import { safeNextPath } from "@/lib/auth/next-path";
import { SIGNED_IN_FLAG_COOKIE } from "@/lib/auth/oauth-providers";

/// 有效期 30 天，和会话的不活跃过期时间一致
const MAX_AGE_SECONDS = 30 * 24 * 60 * 60;

/// `Secure` 开关 `PATRA_SESSION_COOKIE_SECURE`：不设或为 `true` 时开，为 `false` 时关，其他值报错。
/// 每次读写 Cookie 时读取。
export function secureEnabled(): boolean {
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

/// 会话 Cookie 的名字（隐私政策的 Cookie 表按它写）
export function sessionCookieName(): string {
  return sessionCookie().name;
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
export const DEVICE_MAX_AGE_SECONDS = 400 * 24 * 60 * 60;

/// 设备标识 Cookie 的名称和固定属性，`Secure` 与会话 Cookie 同一个开关。埋点（PAP-173）在 proxy 里给所有访客下发它
export function deviceCookie() {
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

/// 要写回的一条 Cookie（埋点的设备与会话 Cookie）
export interface CookieWrite {
  value: string;
  options: {
    name: string;
    httpOnly: true;
    sameSite: "lax";
    path: "/";
    secure: boolean;
    maxAge?: number;
  };
}

/// 埋点（PAP-173）的设备与会话 Cookie：`compute` 按请求里的 Cookie 算出这次的 ID 与要写回的几条，这里写回后原样
/// 返回。算法在 `lib/analytics/server/identity-cookies.ts`；Next 的 Cookie 读写只经这个模块
export async function rewriteTrackingCookies<T extends { cookies: readonly CookieWrite[] }>(
  compute: (read: (name: string) => string | undefined) => T,
): Promise<T> {
  const store = await cookies();
  const result = compute((name) => store.get(name)?.value);
  for (const cookie of result.cookies) {
    store.set({ ...cookie.options, value: cookie.value });
  }
  return result;
}

/// 第三方登录的发起页：登录页、注册页，或绑定时的设置页
export type OAuthFrom = "login" | "register" | "settings";

/// 跨越第三方往返要记住的内容
export interface OAuthReturn {
  from: OAuthFrom;
  /// 成功后回到哪里，读出时已经过 `safeNextPath`
  next: string;
}

/// 发起到回调之间的 Cookie：`Path` 只给 OAuth 路由，10 分钟，和 identity 的授权请求活得一样久。`Path` 不是 `/`，
/// 不能带 `__Host-` 前缀；`Lax`：第三方跳回来是顶层导航，Cookie 照常带上
function oauthFlowCookie(name: string) {
  return {
    name,
    httpOnly: true,
    sameSite: "lax",
    path: "/api/auth/oauth",
    secure: secureEnabled(),
  } as const;
}

const OAUTH_STATE_COOKIE = "patra_oauth_state";
const OAUTH_RETURN_COOKIE = "patra_oauth_return";
const OAUTH_FLOW_MAX_AGE_SECONDS = 10 * 60;

/// 发起第三方授权时写入：`state` 证明回调是这个浏览器发起的，`ret` 记住发起页和 `next`
export async function writeOAuthFlowCookies(state: string, ret: OAuthReturn): Promise<void> {
  const store = await cookies();
  const maxAge = OAUTH_FLOW_MAX_AGE_SECONDS;
  store.set({ ...oauthFlowCookie(OAUTH_STATE_COOKIE), value: state, maxAge });
  store.set({
    ...oauthFlowCookie(OAUTH_RETURN_COOKIE),
    value: new URLSearchParams({ from: ret.from, next: ret.next }).toString(),
    maxAge,
  });
}

/// 回调时读出：没有 `state` Cookie 时为 null；发起页不认识按登录页，`next` 重新校验
export async function readOAuthFlow(): Promise<{ state: string | null; ret: OAuthReturn }> {
  const store = await cookies();
  const state = store.get(OAUTH_STATE_COOKIE)?.value;
  const raw = new URLSearchParams(store.get(OAUTH_RETURN_COOKIE)?.value ?? "");
  const from = raw.get("from");
  return {
    state: state ? state : null,
    ret: {
      from: from === "register" || from === "settings" ? from : "login",
      next: safeNextPath(raw.get("next")),
    },
  };
}

/// 回调核对之后清掉，一次授权只用一次
export async function clearOAuthFlowCookies(): Promise<void> {
  const store = await cookies();
  store.set({ ...oauthFlowCookie(OAUTH_STATE_COOKIE), value: "", maxAge: 0 });
  store.set({ ...oauthFlowCookie(OAUTH_RETURN_COOKIE), value: "", maxAge: 0 });
}

/// 待补填凭据的 Cookie：只放 Cookie，不进地址栏；30 分钟，和 identity 的待补填记录一样
function ticketCookie() {
  return {
    name: "patra_oauth_ticket",
    httpOnly: true,
    sameSite: "lax",
    path: "/",
    secure: secureEnabled(),
  } as const;
}

const TICKET_MAX_AGE_SECONDS = 30 * 60;

/// 写入待补填凭据
export async function writeTicketCookie(ticket: string): Promise<void> {
  (await cookies()).set({ ...ticketCookie(), value: ticket, maxAge: TICKET_MAX_AGE_SECONDS });
}

/// 读待补填凭据；没有或值为空时返回 null
export async function readTicket(): Promise<string | null> {
  const value = (await cookies()).get(ticketCookie().name)?.value;
  return value ? value : null;
}

/// 账号建好后清掉
export async function clearTicketCookie(): Promise<void> {
  (await cookies()).set({ ...ticketCookie(), value: "", maxAge: 0 });
}

/// 第三方登录是整页跳转，落地页不知道刚登录过：写一个脚本能读的短期标记，下一页弹「已登录」后清掉（`SignedInToast`）。
/// 名字和页面上读的那份共用（`oauth-providers.ts`）
export async function writeSignedInFlag(): Promise<void> {
  (await cookies()).set({
    name: SIGNED_IN_FLAG_COOKIE,
    value: "1",
    httpOnly: false,
    sameSite: "lax",
    path: "/",
    secure: secureEnabled(),
    maxAge: 60,
  });
}
