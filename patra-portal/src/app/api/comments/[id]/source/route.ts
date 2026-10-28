import type { NextResponse } from "next/server";
import { fetchSource } from "@/lib/interaction/discussion";
import { readReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// 编辑原文（作者本人）：`<@ID>` 已换回 `@当前用户名`
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ id: string }> },
): Promise<NextResponse> {
  const { id } = await params;
  return readReply(await fetchSource(id));
}
