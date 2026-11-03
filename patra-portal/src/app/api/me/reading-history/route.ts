import type { NextResponse } from "next/server";
import { fetchReadingHistory } from "@/lib/analytics/history";
import { readReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// 我的阅读历史一页（配好卡片）：`cursor` 是下一页，不透明、原样转给行为数据服务。未登录 401，取不到 502
export async function GET(request: Request): Promise<NextResponse> {
  const cursor = new URL(request.url).searchParams.get("cursor") ?? undefined;
  return readReply(await fetchReadingHistory(cursor));
}
