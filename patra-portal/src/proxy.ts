import { type NextRequest, NextResponse } from "next/server";

/// 详情页 404 状态码。详情页有 loading.tsx，服务器先回 200 把骨架流出去，等页面发现文献不存在时
/// 状态码已改不了。这里只拦地址栏直接打开 / 刷新的文档请求：先确认存在，不存在就改写到 /missing/*，
/// 由那里的 notFound() 回真正的 404。站内跳转照常秒出骨架。
const DETAIL = {
  papers: { kind: "paper", endpoint: "publications" },
  journals: { kind: "journal", endpoint: "venues" },
} as const;

const CHECK_TIMEOUT_MS = 3_000;

export async function proxy(req: NextRequest) {
  // 站内跳转与预取是页面里发起的 fetch，本来就不看 HTTP 状态码，直接放行。
  // 认它们靠浏览器自带的 Sec-Fetch-Dest（fetch 是 empty，地址栏打开是 document）：
  // Next 交给这里之前会剥掉 rsc 等内部请求头，在这里读不到。
  // 爬虫、curl 不带这个头，按直接打开处理，照样能拿到真正的 404
  if (req.headers.get("sec-fetch-dest") === "empty") {
    return NextResponse.next();
  }
  const [, section, id = ""] = req.nextUrl.pathname.split("/");
  const { kind, endpoint } = DETAIL[section as keyof typeof DETAIL];
  if (!/^[1-9]\d*$/.test(id)) {
    return NextResponse.rewrite(new URL(`/missing/${kind}`, req.url));
  }
  // 后端出错或超时不在这里拦：放行后由页面照原路报错，不让中间层多等
  const status = await fetch(
    `${process.env.PATRA_GATEWAY_BASE_URL}/patra-catalog/portal/${endpoint}/${id}`,
    { method: "HEAD", signal: AbortSignal.timeout(CHECK_TIMEOUT_MS) },
  ).then(
    (res) => res.status,
    () => null,
  );
  // 与详情页取数一致：404 不存在，400 / 422 是后端拒绝了 id，对读者同样是「没有这一页」
  if (status === 404 || status === 400 || status === 422) {
    return NextResponse.rewrite(new URL(`/missing/${kind}`, req.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/papers/:id", "/journals/:id"],
};
