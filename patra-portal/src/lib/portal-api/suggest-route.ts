import "server-only";
import { NextResponse } from "next/server";
import { normalizeSuggestQuery, suggestQueryLength } from "@/lib/suggest-query";

/// 两个建议接口的 Route Handler 共用：取 `q` 归一，不在 2–100 个码点就直接回空列表、不打网关；
/// 网关失败回 502 + 空列表，选择器显示「搜索暂时不可用」。
export async function suggestRoute<T>(
  request: Request,
  fetchItems: (q: string) => Promise<T[]>,
): Promise<NextResponse> {
  const q = normalizeSuggestQuery(new URL(request.url).searchParams.get("q") ?? "");
  const length = suggestQueryLength(q);
  if (length < 2 || length > 100) {
    return NextResponse.json({ items: [] });
  }
  try {
    return NextResponse.json({ items: await fetchItems(q) });
  } catch {
    return NextResponse.json({ items: [] }, { status: 502 });
  }
}
