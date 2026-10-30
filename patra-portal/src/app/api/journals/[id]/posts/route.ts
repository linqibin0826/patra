import type { NextResponse } from "next/server";
import { parseExperienceSort } from "@/lib/experience/sort";
import { fetchJournalExperiences } from "@/lib/interaction/experience";
import { readReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// 期刊的一页投稿经验（「加载更多」、换排序）；`sort` 是 `latest` / `helpful`（其余按最新投稿），`cursor` 是下一页
export async function GET(
  request: Request,
  { params }: { params: Promise<{ id: string }> },
): Promise<NextResponse> {
  const { id } = await params;
  const query = new URL(request.url).searchParams;
  return readReply(
    await fetchJournalExperiences(
      id,
      parseExperienceSort(query.get("sort")),
      query.get("cursor") ?? undefined,
    ),
  );
}
