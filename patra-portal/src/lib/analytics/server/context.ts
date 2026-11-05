import "server-only";
import { headers } from "next/headers";
import { unstable_rethrow } from "next/navigation";
import { pageTypeOf } from "@/lib/analytics/page-type";
import type { EventPage } from "@/lib/analytics/server/envelope";
import { ensureTrackingIds, TRACKING_HEADERS } from "@/lib/analytics/server/identity-cookies";
import { readSessionToken } from "@/lib/auth/session-cookie";
import { type ClientOrigin, clientOriginFromHeaders } from "@/lib/client-origin";

/// 契约里 `referrer_host` 的格式
const HOST_PATTERN = /^[A-Za-z0-9.-]{1,253}$/;

/// 一次请求里组装服务端事件要用的东西。页面的 `after` 回调不能再读请求 API，所以渲染时读好放进这里
export interface RequestFacts {
  deviceId: string;
  sessionId: string;
  /// 会话令牌，用来在响应之后问 identity 当前用户；没登录为 null
  token: string | null;
  client: ClientOrigin;
  /// 外站来源的主机名；站内跳转、没有来源为 null
  referrerHost: string | null;
  /// Server Action 的请求（带 `Next-Action` 头）：页面因它重新渲染时不算一次浏览
  isActionRequest: boolean;
}

/// `Referer` 的主机名；与本次请求的主机（先 `X-Forwarded-Host` 再 `Host`）相同、没有或解析不了时为 null
function referrerHostOf(h: Headers): string | null {
  const referer = h.get("referer");
  if (!referer) {
    return null;
  }
  let url: URL;
  try {
    url = new URL(referer);
  } catch {
    return null;
  }
  const self = h.get("x-forwarded-host") ?? h.get("host");
  if (url.host === self) {
    return null;
  }
  return HOST_PATTERN.test(url.hostname) ? url.hostname : null;
}

function factsFrom(
  h: Headers,
  ids: { deviceId: string; sessionId: string },
  token: string | null,
): RequestFacts {
  return {
    ...ids,
    token,
    client: clientOriginFromHeaders(h),
    referrerHost: referrerHostOf(h),
    isActionRequest: h.has("next-action"),
  };
}

/// 页面渲染时读：两个 ID 来自 proxy 写的请求头（第一次访问时请求里还没有 Cookie）。缺一个就返回 null，不发事件
export async function readPageFacts(): Promise<RequestFacts | null> {
  try {
    const h = await headers();
    const deviceId = h.get(TRACKING_HEADERS.device);
    const sessionId = h.get(TRACKING_HEADERS.session);
    if (!deviceId || !sessionId) {
      return null;
    }
    return factsFrom(h, { deviceId, sessionId }, await readSessionToken());
  } catch (error) {
    // 静态渲染时 Next 用抛错切到动态渲染，这个信号必须原样抛出去
    unstable_rethrow(error);
    return null;
  }
}

/// Server Action 里读：Action 的 POST 发到页面路径，同样经过 proxy，两个 ID 从 proxy 写的请求头读。不在 Action 里写
/// Cookie：写了 Next 会回渲染整页（多打后端、换掉检索列表的 list_id）。Action 请求本身带 `Next-Action`，但它就是
/// 用户的操作，不是回渲染，`isActionRequest` 固定为 false
export async function readActionFacts(): Promise<RequestFacts | null> {
  const facts = await readPageFacts();
  return facts && { ...facts, isActionRequest: false };
}

/// Route Handler 里读：`/api/` 不经 proxy，自己读写两条 Cookie
export async function readHandlerFacts(): Promise<RequestFacts> {
  const ids = await ensureTrackingIds();
  return factsFrom(await headers(), ids, await readSessionToken());
}

/// 本站来源页的路径；外站来源、没有或解析不了为 null
function sameSiteRefererPath(h: Headers): string | null {
  const referer = h.get("referer");
  if (!referer) {
    return null;
  }
  try {
    const url = new URL(referer);
    return url.host === (h.get("x-forwarded-host") ?? h.get("host")) ? url.pathname : null;
  } catch {
    return null;
  }
}

/// Route Handler 里的事件发生在哪：路径是请求路径，页面类型按来源页推断（下载、导出是在哪个页面上点的），
/// 没有本站来源时是 `other`
export function requestPage(request: Request): EventPage {
  const referer = sameSiteRefererPath(request.headers);
  return {
    path: new URL(request.url).pathname,
    type: referer === null ? "other" : pageTypeOf(referer),
  };
}

/// Server Action 里的事件发生在哪：Action 发到当前页面，路径与类型都取来源页
export async function actionPage(): Promise<EventPage> {
  const referer = sameSiteRefererPath(await headers());
  return referer === null
    ? { path: "/", type: "other" }
    : { path: referer, type: pageTypeOf(referer) };
}
