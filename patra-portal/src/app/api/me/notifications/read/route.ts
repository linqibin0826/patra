import type { NextResponse } from "next/server";
import { markAllNotificationsRead } from "@/lib/notification/inbox";
import { forbidden, readReply } from "@/lib/route-reply";
import { isSameOrigin } from "@/lib/same-origin";

export const dynamic = "force-dynamic";

/// 全部已读（两个标签一起），返回新的未读数。写操作校验 `Origin`
export async function PUT(request: Request): Promise<NextResponse> {
  if (!isSameOrigin(request)) {
    return forbidden();
  }
  return readReply(await markAllNotificationsRead());
}
