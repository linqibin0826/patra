import type { NextResponse } from "next/server";
import { fetchNotifications } from "@/lib/notification/inbox";
import { notificationTabSchema } from "@/lib/notification/inbox-schema";
import { readReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// 一个标签下的一页提醒（`tab`：`replies` / `mentions` / `helpful`；`cursor` 是下一页）。标签不认识时 404，不打网关
export async function GET(request: Request): Promise<NextResponse> {
  const query = new URL(request.url).searchParams;
  const tab = notificationTabSchema.safeParse(query.get("tab"));
  if (!tab.success) {
    return readReply({ kind: "not-found" });
  }
  return readReply(await fetchNotifications(tab.data, query.get("cursor") ?? undefined));
}
