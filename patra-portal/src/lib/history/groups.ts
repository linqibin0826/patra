import type { HistoryEntry } from "@/lib/analytics/history-schema";
import { beijingDayKey, formatDayHeading, formatTimeOfDay } from "@/lib/format-time";

/// 阅读历史的一组：同一个北京时间自然日
export interface HistoryDayGroup {
  key: string;
  label: string;
  items: HistoryEntry[];
}

/// 按北京时间的自然日分组：组按出现顺序、组内保持传入顺序（列表已按最近打开时间倒序）。在全部已加载的条目上算，
/// 加载更多后新条目并入已有分组
export function groupHistoryByDay(items: readonly HistoryEntry[], now: number): HistoryDayGroup[] {
  const groups = new Map<string, HistoryDayGroup>();
  for (const item of items) {
    const key = beijingDayKey(item.lastReadAt);
    const group = groups.get(key);
    if (group) {
      group.items.push(item);
    } else {
      groups.set(key, { key, label: formatDayHeading(item.lastReadAt, now), items: [item] });
    }
  }
  return [...groups.values()];
}

/// 行首的时间：这篇最近一次打开的北京时间 `15:42`
export const historyTime = formatTimeOfDay;
