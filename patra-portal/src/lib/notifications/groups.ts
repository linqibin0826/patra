import { type DayGroup, dayGroupOf } from "@/lib/format-time";
import type { Notification, NotificationPage } from "@/lib/notification/inbox-schema";
import { uniqueBy } from "@/lib/unique-by";

/// 提醒列表的一组：今天 / 昨天 / 更早
export interface NotificationGroup {
  group: DayGroup;
  label: "今天" | "昨天" | "更早";
  items: Notification[];
}

const ORDER: readonly { group: DayGroup; label: NotificationGroup["label"] }[] = [
  { group: "today", label: "今天" },
  { group: "yesterday", label: "昨天" },
  { group: "earlier", label: "更早" },
];

/// 已加载的各页拼成一个列表，保持服务端顺序；同一条出现在两页里时只留第一次：有帮助条目当天再被点会往前挪、消息
/// 乱序时还会往回挪（PAP-144 契约），翻页期间可能被两页都带上
export function flattenPages(pages: readonly NotificationPage[]): Notification[] {
  return uniqueBy(
    pages.flatMap((page) => page.items),
    (item) => item.notificationId,
  );
}

/// 按北京时间的自然日分组：固定按今天、昨天、更早排，空组不返回，组内保持传入顺序（不按时间重排）。在全部已加载的
/// 条目上算，加载更多后新条目并入已有分组，同一个分组标题只出现一次
export function groupByDay(items: readonly Notification[], now: number): NotificationGroup[] {
  return ORDER.map(({ group, label }) => ({
    group,
    label,
    items: items.filter((item) => dayGroupOf(item.occurredAt, now) === group),
  })).filter((g) => g.items.length > 0);
}
