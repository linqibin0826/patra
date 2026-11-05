import { type NextRequest, NextResponse } from "next/server";
import {
  resolveTrackingIds,
  TRACKING_HEADERS,
  type TrackingIds,
} from "@/lib/analytics/server/identity-cookies";
import { lookupSessionByToken } from "@/lib/auth/current-user";
import { deletionPendingHref, isFocusPage, safeNextPath } from "@/lib/auth/next-path";
import { readRequestSessionToken } from "@/lib/auth/session-cookie";
import { clientOriginFromHeaders } from "@/lib/client-origin";
import { gatewayFetch } from "@/lib/gateway";

/// 详情页 404 状态码。详情页有 loading.tsx，服务器先回 200 把骨架流出去，等页面发现文献不存在时
/// 状态码已改不了。这里只拦地址栏直接打开 / 刷新的文档请求：先确认存在，不存在就改写到 /missing/*，
/// 由那里的 notFound() 回真正的 404。站内跳转照常秒出骨架。
const DETAIL = {
  papers: { kind: "paper", endpoint: "/patra-catalog/portal/publications" },
  journals: { kind: "journal", endpoint: "/patra-catalog/portal/venues" },
  /// 经验页（PAP-125）：已删除的经验是 200（页面显示「该经验已删除」），不存在才 404
  posts: { kind: "post", endpoint: "/patra-interaction/posts" },
} as const;

const CHECK_TIMEOUT_MS = 3_000;

const AUTH_PAGES = ["/login", "/register", "/register/complete"];

/// 跳转规则 6：已登录的人打开登录页、注册页时，直接 307 到 `next`。只拦 GET：Server Action 是发到
/// 当前页的 POST，放行。站内点链接、预取也要拦，所以不看 `sec-fetch-dest`。没有 Cookie 时不请求网关；
/// 读 Cookie 抛错、令牌失效、网关出错或超时都放行，显示表单。这里不写 Cookie。
async function leaveAuthPage(req: NextRequest, pass: Pass): Promise<NextResponse> {
  if (req.method !== "GET") {
    return pass.next();
  }
  let token: string | null;
  try {
    token = readRequestSessionToken(req);
  } catch {
    return pass.next();
  }
  if (!token) {
    return pass.next();
  }
  const session = await lookupSessionByToken(token, {
    timeoutMs: CHECK_TIMEOUT_MS,
    client: clientOriginFromHeaders(req.headers),
  });
  if (session.status !== "authenticated") {
    return pass.next();
  }
  const next = safeNextPath(req.nextUrl.searchParams.get("next"));
  // 冷静期账号直接去取消注销页，不先到 next 再被拦一次（spec §8.2）
  const target = session.user.status === "PENDING_DELETION" ? deletionPendingHref(next) : next;
  return NextResponse.redirect(new URL(target, req.url), 307);
}

/// 简报 §2.2 规则 3：冷静期账号打开任何带导航栏的页面，都去取消注销页（spec §9.2）。只查同时满足这几条的
/// 请求，其余返回 null、不请求网关：GET、地址栏直接打开（`Sec-Fetch-Dest: document`；站内跳转由导航栏的
/// 客户端兜底）、不是专注布局的页面、带着会话 Cookie。读 Cookie 抛错、令牌失效、网关出错或超时都放行。
async function pendingDeletionRedirect(req: NextRequest): Promise<NextResponse | null> {
  if (
    req.method !== "GET" ||
    req.headers.get("sec-fetch-dest") !== "document" ||
    isFocusPage(req.nextUrl.pathname)
  ) {
    return null;
  }
  let token: string | null;
  try {
    token = readRequestSessionToken(req);
  } catch {
    return null;
  }
  if (!token) {
    return null;
  }
  const session = await lookupSessionByToken(token, {
    timeoutMs: CHECK_TIMEOUT_MS,
    client: clientOriginFromHeaders(req.headers),
  });
  if (session.status !== "authenticated" || session.user.status !== "PENDING_DELETION") {
    return null;
  }
  const here = `${req.nextUrl.pathname}${req.nextUrl.search}`;
  return NextResponse.redirect(new URL(deletionPendingHref(here), req.url), 307);
}

/// 放行与改写：都把 proxy 定好的请求头交给渲染层
interface Pass {
  next(): NextResponse;
  rewrite(url: URL): NextResponse;
}

/// 埋点（PAP-173）：每个页面请求都下发设备与会话 Cookie（站内跳转、预取也算活动），并把这次确定的两个 ID 写进
/// 传给渲染层的请求头，页面与 Server Action 的服务端事件从 `headers()` 读——第一次访问时请求里还没有 Cookie。
/// 浏览器自己带来的同名头先删掉。算 ID 出错（不应发生）时页面照常，只是这次没有埋点。
export async function proxy(req: NextRequest) {
  let ids: TrackingIds | null = null;
  try {
    ids = resolveTrackingIds((name) => req.cookies.get(name)?.value, Date.now());
  } catch {
    ids = null;
  }
  const forward = new Headers(req.headers);
  forward.delete(TRACKING_HEADERS.device);
  forward.delete(TRACKING_HEADERS.session);
  if (ids) {
    forward.set(TRACKING_HEADERS.device, ids.deviceId);
    forward.set(TRACKING_HEADERS.session, ids.sessionId);
  }
  const pass: Pass = {
    next: () => NextResponse.next({ request: { headers: forward } }),
    rewrite: (url) => NextResponse.rewrite(url, { request: { headers: forward } }),
  };
  const res = await route(req, pass);
  for (const cookie of ids?.cookies ?? []) {
    res.cookies.set({ ...cookie.options, value: cookie.value });
  }
  return res;
}

/// 页面请求的路由：登录页跳走、冷静期跳转、详情页先确认存在，其余放行
async function route(req: NextRequest, pass: Pass): Promise<NextResponse> {
  if (AUTH_PAGES.includes(req.nextUrl.pathname)) {
    return leaveAuthPage(req, pass);
  }
  // 冷静期查询先发出去；详情页的存在检查与它并行，结果出来后冷静期跳转优先
  const pending = pendingDeletionRedirect(req);
  // 站内跳转与预取是页面里发起的 fetch，本来就不看 HTTP 状态码，直接放行。
  // 认它们靠浏览器自带的 Sec-Fetch-Dest（fetch 是 empty，地址栏打开是 document）：
  // Next 交给这里之前会剥掉 rsc 等内部请求头，在这里读不到。
  // 爬虫、curl 不带这个头，按直接打开处理，照样能拿到真正的 404
  if (req.headers.get("sec-fetch-dest") === "empty") {
    return pass.next();
  }
  const segments = req.nextUrl.pathname.split("/");
  const [, section = "", id = ""] = segments;
  // 只有 `/papers/:id`、`/journals/:id`、`/posts/:id` 两段的地址是详情页；列表页（`/papers`、`/journals/`）和更深的路径放行。
  // matcher 按解码后的路径匹配，pathname 却保留编码（`/%70apers/1`、`/%6Cogin` 也会进来）：认不出的放行
  if (segments.length !== 3 || id === "" || !Object.hasOwn(DETAIL, section)) {
    return (await pending) ?? pass.next();
  }
  const { kind, endpoint } = DETAIL[section as keyof typeof DETAIL];
  if (!/^[1-9]\d*$/.test(id)) {
    return (await pending) ?? pass.rewrite(new URL(`/missing/${kind}`, req.url));
  }
  // 后端出错或超时不在这里拦：放行后由页面照原路报错，不让中间层多等
  const [redirect, status] = await Promise.all([
    pending,
    gatewayFetch(`${endpoint}/${id}`, {
      method: "HEAD",
      timeoutMs: CHECK_TIMEOUT_MS,
    }).then(
      (res) => res.status,
      () => null,
    ),
  ]);
  if (redirect) {
    return redirect;
  }
  // 与详情页取数一致：404 不存在，400 / 422 是后端拒绝了 id，对读者同样是「没有这一页」
  if (status === 404 || status === 400 || status === 422) {
    return pass.rewrite(new URL(`/missing/${kind}`, req.url));
  }
  return pass.next();
}

/// 全部页面（冷静期跳转要覆盖任何页面），不含 `/api/`、`/_next/` 与带扩展名的静态文件
export const config = {
  matcher: ["/((?!api/|_next/|.*\\.[^/]+$).*)"],
};
