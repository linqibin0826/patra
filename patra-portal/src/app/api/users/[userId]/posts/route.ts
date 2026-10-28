import type { NextResponse } from "next/server";
import { USER_PAGE_SIZE } from "@/lib/experience/server";
import { fetchUserExperiences } from "@/lib/interaction/experience";
import { readReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// 主页「投稿经验」标签的下一页（每页 20 条）

export async function GET(
  request: Request,
  { params }: { params: Promise<{ userId: string }> },
): Promise<NextResponse> {
  const { userId } = await params;
  const cursor = new URL(request.url).searchParams.get("cursor") ?? undefined;
  return readReply(await fetchUserExperiences(userId, { cursor, limit: USER_PAGE_SIZE }));
}
