import "server-only";
import { newDeviceId, newId } from "@/lib/analytics/ids";
import {
  DEVICE_ID_PATTERN,
  DEVICE_MAX_AGE_SECONDS,
  deviceCookie,
  rewriteTrackingCookies,
  secureEnabled,
} from "@/lib/auth/session-cookie";

/// 30 分钟没有活动就换新会话（埋点方案 §6.2）；正好 30 分钟沿用
const SESSION_IDLE_MS = 30 * 60 * 1000;
/// 最后活动时间比现在晚这么多以内算时钟误差，再多就当值被改坏了
const CLOCK_SKEW_MS = 60 * 1000;

const SESSION_VALUE =
  /^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\.(\d{1,15})$/;

/// proxy 把本次请求确定的两个 ID 写进传给渲染层的请求头，页面与 Server Action 从 `headers()` 读
export const TRACKING_HEADERS = {
  device: "x-patra-device-id",
  session: "x-patra-session-id",
} as const;

/// 写 Cookie 用的属性
export interface CookieSpec {
  name: string;
  httpOnly: true;
  sameSite: "lax";
  path: "/";
  secure: boolean;
  maxAge?: number;
}

/// 会话 Cookie：把一次访问里的事件串起来。不设有效期，浏览器关闭即失效；名字与设备 Cookie 同一套
export function sessionTrackingCookie(): CookieSpec {
  const secure = secureEnabled();
  return {
    name: secure ? "__Host-patra_sid" : "patra_sid",
    httpOnly: true,
    sameSite: "lax",
    path: "/",
    secure,
  };
}

/// 会话 Cookie 的值是「会话 ID.最后活动毫秒数」：解析不了、时间戳在未来超过 1 分钟、或已超过 30 分钟没有活动，
/// 就换新的会话 ID；两种情况最后活动都改成 `now`
export function rollSession(
  raw: string | undefined,
  now: number,
): { sessionId: string; value: string } {
  const match = raw === undefined ? null : SESSION_VALUE.exec(raw);
  const last = match ? Number(match[2]) : Number.NaN;
  const reuse =
    match?.[1] !== undefined && last - now <= CLOCK_SKEW_MS && now - last <= SESSION_IDLE_MS;
  const sessionId = reuse && match?.[1] ? match[1] : newId();
  return { sessionId, value: `${sessionId}.${now}` };
}

/// 本次请求的设备与会话 ID，以及要写回的两条 Cookie
export interface TrackingIds {
  deviceId: string;
  sessionId: string;
  cookies: { name: string; value: string; options: CookieSpec }[];
}

/// 按请求里的两条 Cookie 算出设备与会话 ID：设备值格式对就沿用、否则新生成；会话按 `rollSession`。两条都写回
/// （设备顺延到 400 天，会话更新最后活动时间）。`read` 按名字读请求 Cookie，proxy 与 Route Handler 共用
export function resolveTrackingIds(
  read: (name: string) => string | undefined,
  now: number,
): TrackingIds {
  const device: CookieSpec = { ...deviceCookie(), maxAge: DEVICE_MAX_AGE_SECONDS };
  const session = sessionTrackingCookie();
  const stored = read(device.name);
  const deviceId = stored && DEVICE_ID_PATTERN.test(stored) ? stored : newDeviceId();
  const { sessionId, value } = rollSession(read(session.name), now);
  return {
    deviceId,
    sessionId,
    cookies: [
      { name: device.name, value: deviceId, options: device },
      { name: session.name, value, options: session },
    ],
  };
}

/// Route Handler、Server Action 用（它们不经 proxy，或要自己滚动会话）：读请求 Cookie 并写回
export async function ensureTrackingIds(): Promise<TrackingIds> {
  return rewriteTrackingCookies((read) => resolveTrackingIds(read, Date.now()));
}
