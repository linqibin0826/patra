import type { NextResponse } from "next/server";
import { fetchPostRevisions } from "@/lib/interaction/experience";
import { readReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// 投稿经验的修改历史（公开）
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ id: string }> },
): Promise<NextResponse> {
  const { id } = await params;
  return readReply(await fetchPostRevisions(id));
}
