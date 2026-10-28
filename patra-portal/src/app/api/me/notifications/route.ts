import type { NextResponse } from "next/server";
import { notificationTabSchema } from "@/lib/interaction/notification-schema";
import { fetchNotifications } from "@/lib/interaction/notifications";
import { readReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// 一个标签下的一页提醒（`tab`：`replies` / `mentions`；`cursor` 是下一页）。标签不认识时 404，不打网关
export async function GET(request: Request): Promise<NextResponse> {
  const query = new URL(request.url).searchParams;
  const tab = notificationTabSchema.safeParse(query.get("tab"));
  if (!tab.success) {
    return readReply({ kind: "not-found" });
  }
  return readReply(await fetchNotifications(tab.data, query.get("cursor") ?? undefined));
}
