import { type DayGroup, dayGroupOf } from "@/lib/format-time";
import type { Notification } from "@/lib/interaction/notification-schema";

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

/// 按北京时间的自然日分组：固定按今天、昨天、更早排，空组不返回，组内保持传入顺序。在全部已加载的条目上算，
/// 加载更多后新条目并入已有分组，同一个分组标题只出现一次
export function groupByDay(items: readonly Notification[], now: number): NotificationGroup[] {
  return ORDER.map(({ group, label }) => ({
    group,
    label,
    items: items.filter((item) => dayGroupOf(item.createdAt, now) === group),
  })).filter((g) => g.items.length > 0);
}
