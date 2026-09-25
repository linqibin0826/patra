import { NextResponse } from "next/server";
import { fetchVenueSuggestions } from "@/lib/portal-api/venues";

/** 与 BE @Size(max = 200) 对齐，超长输入截断而非报错 */
const MAX_QUERY_LENGTH = 200;

export const dynamic = "force-dynamic";

/**
 * 期刊候选中转：浏览器 → `/api/venues/suggest?q=` → gateway `/portal/venues?q=&pageSize=8`。
 * gateway 地址只在服务端可见，故经此转发。空 q 直接返回 []；后端失败返回 502 + []，不影响整页。
 */
export async function GET(request: Request): Promise<NextResponse> {
  const q = (new URL(request.url).searchParams.get("q") ?? "").trim().slice(0, MAX_QUERY_LENGTH);
  if (!q) {
    return NextResponse.json([]);
  }
  try {
    return NextResponse.json(await fetchVenueSuggestions(q));
  } catch {
    return NextResponse.json([], { status: 502 });
  }
}
