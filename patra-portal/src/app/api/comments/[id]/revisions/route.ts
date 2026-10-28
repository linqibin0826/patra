import type { NextResponse } from "next/server";
import { fetchRevisions } from "@/lib/interaction/discussion";
import { readReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// 修改历史（公开）
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ id: string }> },
): Promise<NextResponse> {
  const { id } = await params;
  return readReply(await fetchRevisions(id));
}
