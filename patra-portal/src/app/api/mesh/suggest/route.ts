import type { NextResponse } from "next/server";
import { fetchMeshSuggestions } from "@/lib/portal-api/suggest";
import { suggestRoute } from "@/lib/portal-api/suggest-route";

export const dynamic = "force-dynamic";

/// MeSH 候选中转：浏览器 → `/api/mesh/suggest?q=` → 网关 catalog MeSH 建议
export function GET(request: Request): Promise<NextResponse> {
  return suggestRoute(request, fetchMeshSuggestions);
}
