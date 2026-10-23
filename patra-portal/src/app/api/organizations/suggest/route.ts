import type { NextResponse } from "next/server";
import { fetchOrganizationSuggestions } from "@/lib/portal-api/suggest";
import { suggestRoute } from "@/lib/portal-api/suggest-route";

export const dynamic = "force-dynamic";

/// 机构候选中转：浏览器 → `/api/organizations/suggest?q=` → 网关 catalog 机构建议
export function GET(request: Request): Promise<NextResponse> {
  return suggestRoute(request, fetchOrganizationSuggestions);
}
