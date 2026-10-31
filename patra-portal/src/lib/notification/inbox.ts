import "server-only";
import { READ_TIMEOUT_MS, type Read, toRead } from "@/lib/interaction/read";
import {
  type NotificationPage,
  type NotificationTab,
  notificationPageSchema,
  type UnreadCounts,
  unreadCountsSchema,
} from "@/lib/notification/inbox-schema";
import { callNotification } from "@/lib/notification/request";

const UNREAD_PATH = "/me/notifications/unread-count";
const LIST_PATH = "/me/notifications";

/// 取未读数的结果：`not-modified` 是网关回了 304（计数没变，没有响应体）
export type UnreadRead =
  | { kind: "ok"; counts: UnreadCounts; etag: string | null }
  | { kind: "not-modified"; etag: string | null }
  | { kind: "signed-out" }
  | { kind: "unavailable" };

/// 未读数：`ifNoneMatch` 原样转给网关，没变化时网关回 304。304 先于解析响应体判断（它没有响应体）
export async function fetchUnreadCounts(ifNoneMatch: string | null): Promise<UnreadRead> {
  const reply = await callNotification(UNREAD_PATH, { timeoutMs: READ_TIMEOUT_MS, ifNoneMatch });
  if (reply.kind === "ok" && reply.res.status === 304) {
    return { kind: "not-modified", etag: reply.res.headers.get("ETag") };
  }
  const read = toRead(reply, unreadCountsSchema, UNREAD_PATH);
  switch (read.kind) {
    case "ok":
      return {
        kind: "ok",
        counts: read.data,
        etag: reply.kind === "ok" ? reply.res.headers.get("ETag") : null,
      };
    case "not-found":
      // 这条接口不会 404：真遇到了按不可用处理
      return { kind: "unavailable" };
    default:
      return read;
  }
}

/// 一个标签下的一页提醒；游标不透明，原样回传
export async function fetchNotifications(
  tab: NotificationTab,
  cursor?: string,
): Promise<Read<NotificationPage>> {
  const query = new URLSearchParams({ tab });
  if (cursor) {
    query.set("cursor", cursor);
  }
  return toRead(
    await callNotification(LIST_PATH, { timeoutMs: READ_TIMEOUT_MS }, { query }),
    notificationPageSchema,
    LIST_PATH,
  );
}

/// 单条已读，返回新的未读数；不存在或不是自己的是 `not-found`
export async function markNotificationRead(notificationId: string): Promise<Read<UnreadCounts>> {
  const path = `/me/notifications/${notificationId}/read`;
  return toRead(
    await callNotification(path, { method: "PUT", timeoutMs: READ_TIMEOUT_MS }),
    unreadCountsSchema,
    path,
  );
}

/// 全部已读（三个标签一起），返回新的未读数
export async function markAllNotificationsRead(): Promise<Read<UnreadCounts>> {
  const path = "/me/notifications/read";
  return toRead(
    await callNotification(path, { method: "PUT", timeoutMs: READ_TIMEOUT_MS }),
    unreadCountsSchema,
    path,
  );
}
