import { NextResponse } from "next/server";
import { fetchUnreadCounts } from "@/lib/notification/inbox";
import { readReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// 只给本人缓存、每次都来问：与网关相同。不能用 `readReply` 的 `no-store`，304 上写成 `no-store` 浏览器会丢掉缓存条目
const REVALIDATE = "no-cache, private";

/// 未读数：浏览器 → 这里 → 网关 `/patra-notification/me/notifications/unread-count`。浏览器的 `If-None-Match` 原样转发；
/// 网关回 304 时原样回 304、不带响应体。ETag 来自网关，门户不自己算
export async function GET(request: Request): Promise<NextResponse> {
  const read = await fetchUnreadCounts(request.headers.get("if-none-match"));
  switch (read.kind) {
    case "not-modified":
      return new NextResponse(null, { status: 304, headers: revalidate(read.etag) });
    case "ok":
      return NextResponse.json(read.counts, { headers: revalidate(read.etag) });
    default:
      return readReply(read);
  }
}

function revalidate(etag: string | null): Record<string, string> {
  return etag ? { ETag: etag, "Cache-Control": REVALIDATE } : { "Cache-Control": REVALIDATE };
}
