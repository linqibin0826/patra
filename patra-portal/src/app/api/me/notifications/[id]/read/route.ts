import type { NextResponse } from "next/server";
import { isEntityId } from "@/lib/library/params";
import { markNotificationRead } from "@/lib/notification/inbox";
import { forbidden, readReply } from "@/lib/route-reply";
import { isSameOrigin } from "@/lib/same-origin";

export const dynamic = "force-dynamic";

/// 单条已读，返回新的未读数。写操作校验 `Origin`；ID 不合格 404，不打网关；网关 404（已清理或不是自己的）照转
export async function PUT(
  request: Request,
  { params }: { params: Promise<{ id: string }> },
): Promise<NextResponse> {
  if (!isSameOrigin(request)) {
    return forbidden();
  }
  const { id } = await params;
  if (!isEntityId(id)) {
    return readReply({ kind: "not-found" });
  }
  return readReply(await markNotificationRead(id));
}
