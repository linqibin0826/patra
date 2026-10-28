import type { NextResponse } from "next/server";
import { fetchSingleThread, isSubject } from "@/lib/interaction/discussion";
import type { SubjectType } from "@/lib/interaction/discussion-schema";
import { readReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// 单楼视图：目标评论所在的整楼；目标已删除或不在这个对象下是 404
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ type: string; key: string; id: string }> },
): Promise<NextResponse> {
  const { type, key, id } = await params;
  if (!isSubject(type, key)) {
    return readReply({ kind: "not-found" });
  }
  return readReply(await fetchSingleThread({ type: type as SubjectType, key }, id));
}
