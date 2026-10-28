import type { NextResponse } from "next/server";
import { fetchConversation } from "@/lib/interaction/discussion";
import { readReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// 「查看对话」：从根评论到这一条的回复链
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ id: string }> },
): Promise<NextResponse> {
  const { id } = await params;
  return readReply(await fetchConversation(id));
}
