import type { NextResponse } from "next/server";
import { parseDiscussionSort } from "@/lib/discussion/location";
import { fetchThreads, isSubject } from "@/lib/interaction/discussion";
import type { SubjectType } from "@/lib/interaction/discussion-schema";
import { readReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// 讨论区一页楼（`sort`：`latest` 最新、`helpful` 最有帮助，其余都是正序；`cursor` 是下一页）。对象参数不合格 404，不打网关
export async function GET(
  request: Request,
  { params }: { params: Promise<{ type: string; key: string }> },
): Promise<NextResponse> {
  const { type, key } = await params;
  if (!isSubject(type, key)) {
    return readReply({ kind: "not-found" });
  }
  const query = new URL(request.url).searchParams;
  return readReply(
    await fetchThreads(
      { type: type as SubjectType, key },
      parseDiscussionSort(query.get("sort")),
      query.get("cursor") ?? undefined,
    ),
  );
}
