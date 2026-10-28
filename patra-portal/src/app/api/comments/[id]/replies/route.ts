import type { NextResponse } from "next/server";
import { fetchReplies } from "@/lib/interaction/discussion";
import { readReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// 「展开更多回复」：一楼剩下的回复，`cursor` 是下一页
export async function GET(
  request: Request,
  { params }: { params: Promise<{ id: string }> },
): Promise<NextResponse> {
  const { id } = await params;
  return readReply(
    await fetchReplies(id, new URL(request.url).searchParams.get("cursor") ?? undefined),
  );
}
