import type { NextResponse } from "next/server";
import { fetchJournalExperiences } from "@/lib/interaction/experience";
import { readReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// 期刊的一页投稿经验（「加载更多」）；`cursor` 是下一页
export async function GET(
  request: Request,
  { params }: { params: Promise<{ id: string }> },
): Promise<NextResponse> {
  const { id } = await params;
  const cursor = new URL(request.url).searchParams.get("cursor") ?? undefined;
  return readReply(await fetchJournalExperiences(id, cursor));
}
