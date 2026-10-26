import type { NextResponse } from "next/server";
import { fetchLibraryOverview } from "@/lib/interaction/library";
import { readReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// 文献库概览（侧栏与收藏浮层共用）：浏览器 → 这里 → 网关 `/patra-interaction/me/library`。只读，不校验 `Origin`
export async function GET(): Promise<NextResponse> {
  return readReply(await fetchLibraryOverview());
}
