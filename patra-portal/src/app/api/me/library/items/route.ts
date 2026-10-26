import type { NextResponse } from "next/server";
import { fetchLibraryPage, type LibraryView } from "@/lib/interaction/library";
import { isEntityId, parseLibrarySort, parsePage } from "@/lib/library/params";
import { readReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// 文献库一页条目（配好卡片）：`view` 为 `all` / `unfiled` / 收藏夹 ID。视图不合格时 404，不打网关
export async function GET(request: Request): Promise<NextResponse> {
  const query = new URL(request.url).searchParams;
  const raw = query.get("view") ?? "all";
  const view: LibraryView | null =
    raw === "all" || raw === "unfiled" ? raw : isEntityId(raw) ? { collectionId: raw } : null;
  if (view === null) {
    return readReply({ kind: "not-found" });
  }
  return readReply(
    await fetchLibraryPage(
      view,
      parseLibrarySort(query.get("sort") ?? undefined),
      parsePage(query.get("page") ?? undefined),
    ),
  );
}
